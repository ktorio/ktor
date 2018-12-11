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
    private lateinit var interceptorContinuation: Continuation<Unit>

    @Test
    fun testStackTraceWalking() {
        val pipeline = Pipeline<Unit, Unit>(phase)
        pipeline.intercept(phase) {
            captureContinuation()
        }

        runBlocking {
            runPipeline(pipeline)
        }

        val frame = interceptorContinuation as CoroutineStackFrame
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

        val filtered = stacktrace
            .replace(Regex(":[0-9]+"), "") // line numbers
            .replace(Regex("\n.*invokeSuspend.*\n"), "\n") // lambdas
        assertEquals(filtered,
            "io/ktor/tests/utils/PipelineStackFramesTest.nestedCapture(PipelineStackFramesTest.kt)\n" +
                "io/ktor/tests/utils/PipelineStackFramesTest.captureContinuation(PipelineStackFramesTest.kt)\n" +
                "io/ktor/tests/utils/PipelineStackFramesTest.runPipeline(PipelineStackFramesTest.kt)\n"
        )
    }

    private suspend fun runPipeline(pipeline: Pipeline<Unit, Unit>) {
        pipeline.execute(Unit, Unit)
        preventTailCall()
    }

    private suspend fun captureContinuation() {
        nestedCapture()
        preventTailCall()
    }

    private suspend fun nestedCapture() {
        suspendCoroutineUninterceptedOrReturn<Unit> {
            interceptorContinuation = it
            Unit
        }
        preventTailCall()
    }

    private fun preventTailCall() {
    }
}
