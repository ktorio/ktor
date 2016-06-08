package org.jetbrains.ktor.tests.pipeline

import kotlinx.support.jdk7.*
import org.jetbrains.ktor.pipeline.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class PipelineTest {
    val callPhase = PipelinePhase("Call")

    fun <T : Any> Pipeline<T>.execute(subject: T): PipelineState {
        try {
            PipelineMachine().execute(subject, this)
        } catch (e: PipelineControlFlow) {
            when (e) {
                is PipelineCompleted -> return PipelineState.Succeeded
                is PipelinePaused -> return PipelineState.Executing
                else -> throw e
            }
        }
    }

    fun Pipeline<String>.intercept(block: PipelineContext<String>.(String) -> Unit) {
        phases.intercept(callPhase, block)
    }


    fun createPipeline(): Pipeline<String> {
        return Pipeline(callPhase)
    }

    @Test
    fun emptyPipeline() {
        val state = createPipeline().execute("some")
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun singleActionPipeline() {
        var value = false
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            value = true
            assertEquals("some", subject)
        }
        val state = pipeline.execute("some")
        assertTrue(value)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun singleActionPipelineWithFinish() {
        var value = false
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onSuccess {
                assertTrue(value)
            }
            onFail {
                fail("This pipeline shouldn't fail")
            }
            assertFalse(value)
            value = true
            assertEquals("some", subject)
        }
        val state = pipeline.execute("some")
        assertTrue(value)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun singleActionPipelineWithFail() {
        var failed = false
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onSuccess {
                fail("This pipeline shouldn't finish")
            }
            onFail {
                assertFalse(failed)
                failed = true
            }
            assertEquals("some", subject)
            throw UnsupportedOperationException()
        }
        val state = pipeline.execute("some")
        assertTrue(failed)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun actionFinishOrder() {
        var count = 0
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onSuccess {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onSuccess {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
        }
        val state = pipeline.execute("some")
        assertEquals(0, count)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun actionFailOrder() {
        var count = 0
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onFail {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
            throw UnsupportedOperationException()
        }
        val state = pipeline.execute("some")
        assertEquals(0, count)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun actionFinishFailOrder() {
        var count = 0
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onFail {
                assertEquals(1, count)
                count--
            }
            onSuccess {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onSuccess {
                assertEquals(2, count)
                count--
                throw UnsupportedOperationException()
            }
            assertEquals(1, count)
            count++
        }
        val state = pipeline.execute("some")
        assertEquals(0, count)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun actionFailFailOrder() {
        var count = 0
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            onFail {
                assertEquals(1, count)
                count--
                assertEquals("1", it.message)
                assertEquals(1, it.getSuppressed().size)
                assertEquals("2", it.getSuppressed()[0].message)
            }
            onSuccess {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
                assertEquals("1", it.message)
                assertEquals(0, it.getSuppressed().size)
                throw UnsupportedOperationException("2")
            }
            assertEquals(1, count)
            count++
            throw UnsupportedOperationException("1")
        }
        val state = pipeline.execute("some")
        assertEquals(0, count)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun pauseResume() {
        var count = 0
        val pipeline = createPipeline()
        pipeline.intercept { subject ->
            assertEquals(0, count)
            count++
            pause()
        }

        pipeline.intercept {
            assertEquals(1, count)
            count++
        }

        val machine = PipelineMachine()
        assertFailsWith<PipelinePaused> {
            machine.execute("some", pipeline)
        }
        assertFailsWith<PipelineCompleted> {
            machine.proceed()
        }
        assertEquals(2, count)
    }

    @Test
    fun fork() {
        var count = 0
        var max = 0
        var secondaryOk = false
        val pipeline = createPipeline()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            assertEquals(0, count)
            count++
            max = Math.max(max, count)
        }

        pipeline.intercept {
            val secondary = createPipeline()
            secondary.intercept { subject ->
                onFail {
                    fail("This pipeline shouldn't fail")
                }
                assertEquals("another", subject)
                secondaryOk = true
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            onFail {
                assertEquals(2, count)
                count--
            }
            assertEquals(1, count)
            count++
            max = Math.max(max, count)
            throw UnsupportedOperationException()
        }
        val state = pipeline.execute("some")
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(0, count)
        assertEquals(2, max)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun forkAndFail() {
        var count = 0
        var max = 0
        var secondaryOk = false
        val pipeline = createPipeline()
        pipeline.intercept {
            onFail {
                assertEquals(1, count)
                count--
            }
            onSuccess {
                fail("This pipeline shouldn't finish")
            }
            assertEquals(0, count)
            count++
            max = Math.max(max, count)
        }

        pipeline.intercept {
            val secondary = createPipeline()
            secondary.intercept { subject ->
                onSuccess {
                    fail("This pipeline shouldn't finish")
                }
                assertEquals("another", subject)
                secondaryOk = true
                throw UnsupportedOperationException()
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            fail("This pipeline shouldn't run")
        }

        val state = pipeline.execute("some")
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(0, count)
        assertEquals(1, max)
        assertEquals(PipelineState.Succeeded, state)
    }

    @Test
    fun asyncPipeline() {
        var count = 0
        val pipeline = createPipeline()
        val latch = CountDownLatch(1)
        pipeline.intercept {
            CompletableFuture.runAsync {
                assertEquals(0, count)
                count++
                proceed()
            }
            pause()
        }

        pipeline.intercept {
            assertEquals(1, count)
            count++
            latch.countDown()
        }

        pipeline.execute("some")
        latch.await()
        assertEquals(2, count)
    }

    @Test
    fun asyncFork() {
        var count = 0
        val pipeline = createPipeline()
        var secondaryOk = false
        val latch = CountDownLatch(1)
        pipeline.intercept {
            onFail {
                latch.countDown()
                fail("This pipeline shouldn't fail")
            }
            CompletableFuture.runAsync {
                assertEquals(0, count)
                count++
            }.whenComplete { v, t -> proceed() }
            pause()
        }

        pipeline.intercept {
            val secondary = createPipeline()
            secondary.intercept { subject ->
                assertEquals("another", subject)
                CompletableFuture.runAsync {
                    secondaryOk = true
                }.whenComplete { v, t -> proceed() }
                pause()
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            assertTrue(secondaryOk)
            assertEquals(1, count)
            count++
            latch.countDown()
        }

        pipeline.execute("some")
        latch.await()
        assertTrue(secondaryOk, "Secondary should be run")
        assertEquals(2, count)
    }

    private fun checkBeforeAfterPipeline(after: PipelinePhase, before: PipelinePhase, pipeline: Pipeline<String>) {
        var value = false
        pipeline.intercept(after) {
            value = true
        }
        pipeline.intercept(before) {
            assertFalse(value)
        }
        val state = pipeline.execute("some")
        assertTrue(value)
        assertEquals(PipelineState.Succeeded, state)
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
            pipeline.intercept(after) {
            }
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

    @Test
    fun executeDuringFailRollup() {
        val pipeline = createPipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail { cause ->
                events.add("pre-failed $value")
            }
            onSuccess {
                events.add("pre-success $value")
            }
        }

        pipeline.intercept { value ->
            events.add("handle $value")

            onFail {
                events.add("failed2 $value")
            }
            onFail { cause ->
                events.add("failed $value")
                machine.execute("B", pipeline)
            }
            onSuccess {
                events.add("success $value")
            }
        }

        pipeline.intercept { value ->
            if (value == "A") {
                throw IllegalStateException("expected")
            }
        }

        machine.runBlockWithResult {
            machine.execute("A", pipeline)
        }

        assertEquals(listOf("handle A", "failed A", "handle B", "success B", "pre-success B", "failed2 A", "pre-failed A"), events)
    }

    @Test
    fun executeDuringSuccessRollup() {
        val pipeline = createPipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail { cause ->
                events.add("pre-failed $value")
            }
            onSuccess {
                events.add("pre-success $value")
            }
        }

        pipeline.intercept { value ->
            events.add("handle $value")

            onFail { cause ->
                events.add("failed $value")
            }
            onSuccess {
                events.add("success $value")
                if (value == "A") {
                    machine.execute("B", pipeline)
                }
            }
            onSuccess {
                events.add("success2 $value")
            }
        }

        machine.runBlockWithResult {
            machine.execute("A", pipeline)
        }

        assertEquals(listOf("handle A", "success2 A", "success A", "handle B", "success2 B", "success B", "pre-success B", "pre-success A"), events)
    }

    @Test
    fun executeFailingDuringSuccessRollup() {
        val pipeline = createPipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail { cause ->
                events.add("pre-failed $value")
            }
            onSuccess {
                events.add("pre-success $value")
            }
        }

        pipeline.intercept { value ->
            events.add("handle $value")

            onFail { cause ->
                events.add("failed $value")
            }
            onSuccess {
                events.add("success $value")
                machine.execute("B", pipeline)
            }

            if (value == "B") {
                throw IllegalStateException("expected")
            }
        }

        machine.runBlockWithResult {
            machine.execute("A", pipeline)
        }

        assertEquals(listOf("handle A", "success A", "handle B", "failed B", "pre-failed B", "pre-failed A"), events)
    }
}
