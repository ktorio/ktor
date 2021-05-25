/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils

import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import kotlin.test.*

class PipelineTest {
    val phase = PipelinePhase("Phase")
    fun pipeline(): Pipeline<String, Unit> = Pipeline(phase)
    fun Pipeline<String, Unit>.intercept(block: PipelineInterceptor<String, Unit>) = intercept(phase, block)
    fun <T : Any> Pipeline<T, Unit>.executeBlocking(subject: T) = runBlocking { execute(Unit, subject) }

    @Test
    fun emptyPipeline() {
        pipeline().executeBlocking("some")
    }

    @Test
    fun singleActionPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject -> events.add("intercept $subject") }
        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept some"), events)
    }

    @Test
    fun implicitProceed() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject -> events.add("intercept1 $subject") }
        pipeline.intercept { subject -> events.add("intercept2 $subject") }
        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some"), events)
    }

    @Test
    fun singleActionPipelineWithFail() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept $subject")
                throw UnsupportedOperationException()
            } catch (e: Throwable) {
                events.add("fail $subject")
            }
        }
        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept some", "fail some"), events)
    }

    @Test
    fun actionFinishOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept1 $subject")
                proceed()
                events.add("success1 $subject")
            } catch (e: Throwable) {
                events.add("fail1 $subject")
            }
        }

        pipeline.intercept { subject ->
            try {
                events.add("intercept2 $subject")
                proceed()
                events.add("success2 $subject")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
            }
        }
        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "success2 some", "success1 some"), events)
    }

    @Test
    fun actionFinishAllOrder() {
        val events = mutableListOf<String>()
        val p1 = pipeline()

        p1.intercept { subject ->
            try {
                events.add("intercept-p1-1 $subject")

                val p2 = pipeline()
                p2.intercept { nested ->
                    events.add("intercept-p2-1 $nested")

                    val p3 = pipeline()
                    p3.intercept { nested2 ->
                        events.add("intercept-p3-1 $nested2")
                        proceed()
                    }
                    p3.intercept { nested2 ->
                        events.add("intercept-p3-2 $nested2")
                        proceed()
                    }
                    p3.execute(Unit, "p3")
                    proceed()
                    events.add("success-p2-1 $nested")
                }

                p2.execute(Unit, "p2")
                proceed()
                events.add("success-p1-1 $subject")
            } catch (t: Throwable) {
                events.add("fail-p1-1 $subject")
                throw t
            }
        }

        p1.intercept { subject ->
            events.add("intercept-p1-2 $subject")
            proceed()
        }

        p1.executeBlocking("p1")
        assertEquals(
            listOf(
                "intercept-p1-1 p1",
                "intercept-p2-1 p2",
                "intercept-p3-1 p3",
                "intercept-p3-2 p3",
                "success-p2-1 p2",
                "intercept-p1-2 p1",
                "success-p1-1 p1"
            ),
            events
        )
    }

    @Test
    fun actionFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept1 $subject")
                proceed()
                events.add("success1 $subject")
            } catch (e: Throwable) {
                events.add("fail1 $subject")
            }
        }

        pipeline.intercept { subject ->
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException()
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw e
            }
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
    }

    @Test
    fun actionFinishFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept1 $subject")
                proceed()
                events.add("success1 $subject")
            } catch (e: Throwable) {
                events.add("fail1 $subject")
            }
        }

        pipeline.intercept { subject ->
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException()
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw e
            }
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
    }

    @Test
    fun actionFailFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept1 $subject")
                proceed()
                events.add("success1 $subject")
            } catch (e: Throwable) {
                events.add("fail1 $subject")
            }
        }

        pipeline.intercept { subject ->
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException("1")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw UnsupportedOperationException("2")
            }
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
    }

    @Test
    fun forkSuccess() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            events.add("intercept1 $subject")
            proceed()
        }

        pipeline.intercept { subject ->
            events.add("intercept2 $subject")

            val secondary = pipeline()
            secondary.intercept { nested ->
                events.add("intercept3 $nested")
                proceed()
            }
            secondary.execute(Unit, "another")
            proceed()
        }

        pipeline.intercept { subject ->
            events.add("intercept4 $subject")
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "intercept4 some"), events)
    }

    @Test
    fun forkFailMain() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            events.add("intercept1 $subject")
            try {
                proceed()
            } catch (t: Throwable) {
                events.add("fail1 $subject")
                throw t
            }
        }

        pipeline.intercept { subject ->
            events.add("intercept2 $subject")
            pipeline().apply {
                intercept { nested -> events.add("intercept3 $nested") }
            }.execute(Unit, "another")
            proceed()
        }

        pipeline.intercept { subject ->
            events.add("intercept4 $subject")
            throw UnsupportedOperationException()
        }

        assertFailsWith<UnsupportedOperationException> {
            pipeline.executeBlocking("some")
        }
        assertEquals(
            listOf(
                "intercept1 some",
                "intercept2 some",
                "intercept3 another",
                "intercept4 some",
                "fail1 some"
            ),
            events
        )
    }

    @Test
    fun forkFailNested() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            events.add("intercept1 $subject")
            try {
                proceed()
            } catch (t: Throwable) {
                events.add("fail1 $subject")
                throw t
            }
        }

        pipeline.intercept { subject ->
            events.add("intercept2 $subject")
            pipeline().apply {
                intercept { nested ->
                    events.add("intercept3 $nested")
                    throw UnsupportedOperationException()
                }
            }.execute(Unit, "another")
            proceed()
        }

        pipeline.intercept { subject ->
            events.add("intercept4 $subject")
        }

        assertFailsWith<UnsupportedOperationException> {
            pipeline.executeBlocking("some")
        }
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "fail1 some"), events)
    }

    @Test
    fun asyncPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            events.add("intercept1 $subject")
            withContext(Dispatchers.Default) {
                events.add("future1 $subject")
                proceed()
            }
            events.add("success1 $subject")
        }

        pipeline.intercept { subject ->
            events.add("intercept2 $subject")
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "future1 some", "intercept2 some", "success1 some"), events)
    }

    @Test
    fun asyncFork() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            events.add("intercept1 $subject")
            withContext(Dispatchers.Default) {
                events.add("future1 $subject")
                proceed()
            }
            events.add("success1 $subject")
        }

        pipeline.intercept {
            val secondary = pipeline()
            secondary.intercept { subject ->
                withContext(Dispatchers.Default) {
                    events.add("intercept2 $subject")
                }
            }
            secondary.execute(Unit, "another")
            proceed()
        }

        pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "future1 some", "intercept2 another", "success1 some"), events)
    }

    private fun checkBeforeAfterPipeline(
        after: PipelinePhase,
        before: PipelinePhase,
        pipeline: Pipeline<String, Unit>
    ) {
        var value = false
        pipeline.intercept(after) {
            value = true
            proceed()
        }
        pipeline.intercept(before) {
            assertFalse(value)
            proceed()
        }
        pipeline.executeBlocking("some")
        assertTrue(value)
    }

    @Test
    fun phased() {
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        val pipeline = Pipeline<String, Unit>(before, after)
        checkBeforeAfterPipeline(after, before, pipeline)
    }

    @Test
    fun phasedNotRegistered() {
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        val pipeline = Pipeline<String, Unit>(before)
        assertFailsWith<InvalidPhaseException> {
            pipeline.intercept(after) {}
        }
    }

    @Test
    fun phasedBefore() {
        val pipeline = Pipeline<String, Unit>()
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        pipeline.addPhase(after)
        pipeline.insertPhaseBefore(after, before)
        checkBeforeAfterPipeline(after, before, pipeline)
    }

    @Test
    fun phasedAfter() {
        val pipeline = Pipeline<String, Unit>()
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        pipeline.addPhase(before)
        pipeline.insertPhaseAfter(before, after)
        checkBeforeAfterPipeline(after, before, pipeline)
    }

    private suspend fun interceptor1(context: PipelineContext<String, Unit>, content: String) {
        yield()
        context.proceedWith("$content first")
    }

    private suspend fun interceptor2(context: PipelineContext<String, Unit>, content: String) {
        yield()
        context.proceedWith("$content second")
    }

    private suspend fun interceptor3(context: PipelineContext<String, Unit>, content: String) {
        yield()
        error(content)
    }
}
