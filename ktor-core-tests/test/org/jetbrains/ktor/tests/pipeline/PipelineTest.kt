package org.jetbrains.ktor.tests.pipeline

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.pipeline.*
import org.junit.*
import kotlin.test.*

class PipelineTest {
    val callPhase = PipelinePhase("Call")
    fun pipeline(): Pipeline<String> = Pipeline(callPhase)
    fun Pipeline<String>.intercept(block: PipelineInterceptor<String>) = phases.intercept(callPhase, block)
    fun <T : Any> Pipeline<T>.executeBlocking(subject: T): PipelineState {
        try {
            runBlocking(Here) { execute(subject) }
        } catch (t: Throwable) {
            return PipelineState.Failed
        }
        return PipelineState.Finished
    }

    @Test
    fun emptyPipeline() {
        val state = pipeline().executeBlocking("some")
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { events.add("intercept $subject") }
        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun implicitProceed() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { events.add("intercept1 $subject") }
        pipeline.intercept { events.add("intercept2 $subject") }
        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionPipelineWithFail() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            try {
                events.add("intercept $subject")
                throw UnsupportedOperationException()
                events.add("success $subject")
            } catch (e: Throwable) {
                events.add("fail $subject")
            }
        }
        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept some", "fail some"), events)
        assertEquals(PipelineState.Finished, state)
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

        pipeline.intercept {
            try {
                events.add("intercept2 $subject")
                proceed()
                events.add("success2 $subject")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
            }
        }
        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "success2 some", "success1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFinishAllOrder() {
        val events = mutableListOf<String>()
        val p1 = pipeline()

        p1.intercept {
            try {
                events.add("intercept-p1-1 $subject")

                val p2 = pipeline()
                p2.intercept {
                    events.add("intercept-p2-1 $subject")

                    val p3 = pipeline()
                    p3.intercept {
                        events.add("intercept-p3-1 $subject")
                        proceed()
                    }
                    p3.intercept {
                        events.add("intercept-p3-2 $subject")
                        proceed()
                    }
                    p3.execute("p3")
                    proceed()
                    events.add("success-p2-1 $subject")
                }

                p2.execute("p2")
                proceed()
                events.add("success-p1-1 $subject")
            } catch(t: Throwable) {
                events.add("fail-p1-1 $subject")
                throw t
            }
        }

        p1.intercept {
            events.add("intercept-p1-2 $subject")
            proceed()
        }

        val state = p1.executeBlocking("p1")
        assertEquals(listOf(
                "intercept-p1-1 p1",
                "intercept-p2-1 p2",
                "intercept-p3-1 p3",
                "intercept-p3-2 p3",
                "success-p2-1 p2",
                "intercept-p1-2 p1",
                "success-p1-1 p1"
        ), events)
        assertEquals(PipelineState.Finished, state)
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

        pipeline.intercept {
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException()
                events.add("success2 $subject")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw e
            }
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
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

        pipeline.intercept {
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException()
                events.add("success2 $subject")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw e
            }
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
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

        pipeline.intercept {
            try {
                events.add("intercept2 $subject")
                throw UnsupportedOperationException("1")
                events.add("success2 $subject")
            } catch (e: Throwable) {
                events.add("fail2 $subject")
                throw UnsupportedOperationException("2")
            }
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }


    @Test
    fun forkSuccess() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            events.add("intercept1 $subject")
            proceed()
        }

        pipeline.intercept {
            events.add("intercept2 $subject")

            val secondary = pipeline()
            secondary.intercept { subject ->
                events.add("intercept3 $subject")
                proceed()
            }
            secondary.execute("another")
            proceed()
        }

        pipeline.intercept {
            events.add("intercept4 $subject")
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "intercept4 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun forkFailMain() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            events.add("intercept1 $subject")
            try {
                proceed()
            } catch (t: Throwable) {
                events.add("fail1 $subject")
                throw t
            }
        }

        pipeline.intercept {
            events.add("intercept2 $subject")
            pipeline().apply {
                intercept { events.add("intercept3 $subject") }
            }.execute("another")
            proceed()
        }

        pipeline.intercept {
            events.add("intercept4 $subject")
            throw UnsupportedOperationException()
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another",
                "intercept4 some", "fail1 some"), events)

        assertEquals(PipelineState.Failed, state)
    }

    @Test
    fun forkFailNested() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            events.add("intercept1 $subject")
            try {
                proceed()
            } catch (t: Throwable) {
                events.add("fail1 $subject")
                throw t
            }
        }

        pipeline.intercept {
            events.add("intercept2 $subject")
            pipeline().apply {
                intercept {
                    events.add("intercept3 $subject")
                    throw UnsupportedOperationException()
                }
            }.execute("another")
            proceed()
        }

        pipeline.intercept {
            events.add("intercept4 $subject")
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "fail1 some"), events)
        assertEquals(PipelineState.Failed, state)
    }

    @Test
    fun asyncPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            events.add("intercept1 $subject")
            future {
                events.add("future1 $subject")
                proceed()
            }.await()
            events.add("success1 $subject")
        }

        pipeline.intercept {
            events.add("intercept2 $subject")
        }

        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "future1 some", "intercept2 some", "success1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun asyncFork() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            events.add("intercept1 $subject")
            future {
                events.add("future1 $subject")
                proceed()
            }.await()
            events.add("success1 $subject")
        }

        pipeline.intercept {
            val secondary = pipeline()
            secondary.intercept {
                future {
                    events.add("intercept2 $subject")
                }.await()
            }
            secondary.execute("another")
            proceed()
        }


        val state = pipeline.executeBlocking("some")
        assertEquals(listOf("intercept1 some", "future1 some", "intercept2 another", "success1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    private fun checkBeforeAfterPipeline(after: PipelinePhase, before: PipelinePhase, pipeline: Pipeline<String>) {
        var value = false
        pipeline.intercept(after) {
            value = true
            proceed()
        }
        pipeline.intercept(before) {
            assertFalse(value)
            proceed()
        }
        val state = pipeline.executeBlocking("some")
        assertTrue(value)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun phased() {
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        val pipeline = Pipeline<String>(before, after)
        checkBeforeAfterPipeline(after, before, pipeline)
    }

    @Test
    fun phasedNotRegistered() {
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        val pipeline = Pipeline<String>(before)
        assertFailsWith<InvalidPhaseException> {
            pipeline.intercept(after) {}
        }
    }

    @Test
    fun phasedBefore() {
        val pipeline = Pipeline<String>()
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        pipeline.phases.add(after)
        pipeline.phases.insertBefore(after, before)
        checkBeforeAfterPipeline(after, before, pipeline)
    }

    @Test
    fun phasedAfter() {
        val pipeline = Pipeline<String>()
        val before = PipelinePhase("before")
        val after = PipelinePhase("after")
        pipeline.phases.add(before)
        pipeline.phases.insertAfter(before, after)
        checkBeforeAfterPipeline(after, before, pipeline)
    }
}
