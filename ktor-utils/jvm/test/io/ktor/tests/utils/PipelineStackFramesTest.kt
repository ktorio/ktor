package io.ktor.tests.utils

import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.test.*

class PipelineStackFramesTest {
    private val phase = PipelinePhase("StubPhase")
    private lateinit var capturedStackTrace: String
    private lateinit var capturedStackTrace2: String

    @Test
    fun testStackTraceWalking() {
        val pipeline = Pipeline<Unit, Unit>(phase)
        pipeline.intercept(phase) {
            captureStackTrace()
        }

        runBlocking {
            runPipeline(pipeline)
        }

        assertEquals(capturedStackTrace,
            "io.ktor.tests.utils.PipelineStackFramesTest.nestedCapture(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.captureStackTrace(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.runPipeline(PipelineStackFramesTest.kt)\n")

        assertEquals(capturedStackTrace2,
            "io.ktor.tests.utils.PipelineStackFramesTest.nestedCapture(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.captureStackTrace(PipelineStackFramesTest.kt)\n")
    }

    @Test
    fun testNestedStackTraceWalking() {
        val pipeline = Pipeline<Unit, Unit>(phase)
        pipeline.intercept(phase) {
            callProceed()
        }

        pipeline.intercept(phase) {
            captureStackTrace()
        }

        runBlocking {
            runPipeline(pipeline)
        }

        assertEquals(capturedStackTrace,
            "io.ktor.tests.utils.PipelineStackFramesTest.nestedCapture(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.captureStackTrace(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.callProceed(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.runPipeline(PipelineStackFramesTest.kt)\n")


        assertEquals(capturedStackTrace2,
            "io.ktor.tests.utils.PipelineStackFramesTest.nestedCapture(PipelineStackFramesTest.kt)\n" +
                "io.ktor.tests.utils.PipelineStackFramesTest.captureStackTrace(PipelineStackFramesTest.kt)\n")
    }

    private suspend fun PipelineContext<Unit, Unit>.callProceed() {
        proceed()
        preventTailCall()
    }

    private fun interceptorStackTrace(continuation: Continuation<*>): String {
        val frame = continuation as CoroutineStackFrame
        val stacktrace = buildString {
            var currentTop: CoroutineStackFrame? = frame
            while (currentTop != null) {
                val element = currentTop.getStackTraceElement()
                if (element != null) {
                    append(element)
                    append('\n')
                }
                currentTop = currentTop.callerFrame
            }
        }
        return stacktrace
            .replace(Regex(":[0-9]+"), "") // line numbers
            .replace(Regex("\n.*invokeSuspend.*\n"), "\n")
    }

    private suspend fun runPipeline(pipeline: Pipeline<Unit, Unit>) {
        pipeline.execute(Unit, Unit)
        preventTailCall()
    }

    private suspend fun captureStackTrace() {
        nestedCapture()
        preventTailCall()
    }

    private suspend fun nestedCapture() {
        suspendCoroutineUninterceptedOrReturn<Unit> {
            capturedStackTrace = interceptorStackTrace(it)
            capturedStackTrace2 = interceptorStackTrace(it)
            Unit
        }
        preventTailCall()
    }

    private fun preventTailCall() {
    }
}
