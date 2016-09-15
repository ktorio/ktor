package org.jetbrains.ktor.tests.pipeline

import kotlinx.support.jdk7.*
import org.jetbrains.ktor.pipeline.*
import org.junit.*
import java.util.concurrent.*
import kotlin.test.*

class PipelineTest {
    val callPhase = PipelinePhase("Call")

    fun pipeline(): Pipeline<String> = Pipeline(callPhase)

    fun Pipeline<String>.intercept(block: PipelineContext<String>.(String) -> Unit) = phases.intercept(callPhase, block)

    fun <T : Any> Pipeline<T>.execute(subject: T): PipelineState {
        try {
            PipelineMachine().execute(subject, this)
        } catch (e: PipelineControl) {
            when (e) {
                is PipelineControl.Completed -> return PipelineState.Finished
                is PipelineControl.Paused -> return PipelineState.Executing
                else -> throw e
            }
        }
    }

    @Test
    fun emptyPipeline() {
        val state = pipeline().execute("some")
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject -> events.add("intercept $subject") }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionRepeatPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        var repeated = false
        pipeline.intercept { subject ->
            events.add("intercept $subject (repeated = $repeated)")
            if (!repeated) {
                repeated = true
                repeat()
            }
        }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept some (repeated = false)", "intercept some (repeated = true)"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionRepeatFailPipeline() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        var repeated = false
        pipeline.intercept { subject ->
            onSuccess {
                events.add("success $subject")
            }
            onFail {
                events.add("fail $subject")
            }
            events.add("intercept $subject (repeated = $repeated)")
            if (!repeated) {
                repeated = true
                repeat()
            } else {
                throw IllegalStateException()
            }
        }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept some (repeated = false)", "intercept some (repeated = true)", "fail some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionPipelineWithFinish() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess {
                events.add("success $subject")
            }
            onFail {
                events.add("fail $subject")
            }
            events.add("intercept $subject")
        }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept some", "success some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun singleActionPipelineWithFail() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success $subject") }
            onFail { events.add("fail $subject") }
            events.add("intercept $subject")
            throw UnsupportedOperationException()
        }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept some", "fail some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFinishOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")
        }
        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "success2 some", "success1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFinishAllOrder() {
        val events = mutableListOf<String>()
        val p1 = pipeline()

        p1.intercept {
            onSuccess { events.add("success-p1-1 $subject") }
            onFail { events.add("fail-p1-1 $subject") }
            events.add("intercept-p1-1 $subject")

            val p2 = pipeline()
            p2.intercept {
                onSuccess { events.add("success-p2-1 $subject") }
                onFail { events.add("fail-p2-1 $subject") }
                events.add("intercept-p2-1 $subject")

                val p3 = pipeline()
                p3.intercept { subject ->
                    onSuccess {
                        events.add("success-p3-1 $subject")
                        finishAll()
                    }
                    onFail { events.add("fail-p3-1 $subject") }
                    events.add("intercept-p3-1 $subject")
                }
                p3.intercept {
                    onSuccess { events.add("success-p3-2 $subject") }
                    onFail { events.add("fail-p3-2 $subject") }
                    events.add("intercept-p3-2 $subject")
                }

                fork("p3", p3)
            }

            fork("p2", p2)
        }

        p1.intercept {
            onSuccess { events.add("success-p1-2 $subject") }
            onFail { events.add("fail-p1-2 $subject") }
            events.add("intercept-p1-2 $subject")
        }

        val state = p1.execute("p1")
        assertEquals(listOf(
                "intercept-p1-1 p1",
                "intercept-p2-1 p2",
                "intercept-p3-1 p3",
                "intercept-p3-2 p3",
                "success-p3-2 p3",
                "success-p3-1 p3",
                "success-p2-1 p2",
                "success-p1-1 p1"
        ), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")
            throw UnsupportedOperationException()
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFinishFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess {
                events.add("success2 $subject")
                throw UnsupportedOperationException()
            }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "success2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun actionFailFailOrder() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success1 $subject") }
            onFail {
                events.add("fail1 $subject")
                assertNotNull(exception)
                assertEquals("1", exception!!.message)
                assertEquals(1, exception!!.getSuppressed().size)
                assertEquals("2", exception!!.getSuppressed()[0].message)
            }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail {
                events.add("fail2 $subject")
                throw UnsupportedOperationException("2")
            }
            events.add("intercept2 $subject")
            throw UnsupportedOperationException("1")
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun pauseResume() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept { subject ->
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
            pause()
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")
        }

        val machine = PipelineMachine()
        assertFailsWith<PipelineControl.Paused> {
            machine.execute("some", pipeline)
        }
        assertFailsWith<PipelineControl.Completed> {
            machine.proceed()
        }
        assertEquals(listOf("intercept1 some", "intercept2 some", "success2 some", "success1 some"), events)
    }

    @Test
    fun forkSuccess() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")

            val secondary = pipeline()
            secondary.intercept { subject ->
                onSuccess { events.add("success3 $subject") }
                onFail { events.add("fail3 $subject") }
                events.add("intercept3 $subject")
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            onSuccess { events.add("success4 $subject") }
            onFail { events.add("fail4 $subject") }
            events.add("intercept4 $subject")
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "success3 another",
                "intercept4 some", "success4 some", "success2 some", "success1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun forkFailMain() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")

            val secondary = pipeline()
            secondary.intercept { subject ->
                onSuccess { events.add("success3 $subject") }
                onFail { events.add("fail3 $subject") }
                events.add("intercept3 $subject")
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            onSuccess { events.add("success4 $subject") }
            onFail { events.add("fail4 $subject") }
            events.add("intercept4 $subject")
            throw UnsupportedOperationException()
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "success3 another",
                "intercept4 some", "fail4 some", "fail2 some", "fail1 some"), events)

        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun forkFailNested() {
        val events = mutableListOf<String>()
        val pipeline = pipeline()
        pipeline.intercept {
            onSuccess { events.add("success1 $subject") }
            onFail { events.add("fail1 $subject") }
            events.add("intercept1 $subject")
        }

        pipeline.intercept {
            onSuccess { events.add("success2 $subject") }
            onFail { events.add("fail2 $subject") }
            events.add("intercept2 $subject")

            val secondary = pipeline()
            secondary.intercept { subject ->
                onSuccess { events.add("success3 $subject") }
                onFail { events.add("fail3 $subject") }
                events.add("intercept3 $subject")
                throw UnsupportedOperationException()
            }
            fork("another", secondary)
        }

        pipeline.intercept {
            onSuccess { events.add("success4 $subject") }
            onFail { events.add("fail4 $subject") }
            events.add("intercept4 $subject")
        }

        val state = pipeline.execute("some")
        assertEquals(listOf("intercept1 some", "intercept2 some", "intercept3 another", "fail3 another",
                "fail2 some", "fail1 some"), events)
        assertEquals(PipelineState.Finished, state)
    }

    @Test
    fun asyncPipeline() {
        var count = 0
        val pipeline = pipeline()
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
        val pipeline = pipeline()
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
            val secondary = pipeline()
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
        val pipeline = pipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail {
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
            onFail {
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
        val pipeline = pipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail {
                events.add("pre-failed $value")
            }
            onSuccess {
                events.add("pre-success $value")
            }
        }

        pipeline.intercept { value ->
            events.add("handle $value")

            onFail {
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
        val pipeline = pipeline()
        val events = mutableListOf<String>()
        val machine = PipelineMachine()

        pipeline.intercept { value ->
            onFail {
                events.add("pre-failed $value")
            }
            onSuccess {
                events.add("pre-success $value")
            }
        }

        pipeline.intercept { value ->
            events.add("handle $value")

            onFail {
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
