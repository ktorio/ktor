/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class PipelineContractsJvmTest {

    // Regression test for KTOR-9431.
    @Test
    fun `thread context is not leaked into outer interceptor after proceed`() {
        val threadLocal = ThreadLocal<String?>()
        val phase = PipelinePhase("test")
        val dispatcher = ExternalEventLoopDispatcher()

        dispatcher.runEventLoop {
            val pipeline = Pipeline<Unit, Unit>(phase)
            var capturedValue: String? = null

            pipeline.intercept(phase) {
                proceed()
                capturedValue = threadLocal.get()
            }

            pipeline.intercept(phase) {
                withContext(threadLocal.asContextElement("set")) {
                    proceed()
                }
            }

            pipeline.intercept(phase) {
                yield()
            }

            pipeline.execute(Unit, Unit)

            assertNull(threadLocal.get(), "SFG leaked thread local after pipeline")
            assertNull(capturedValue, "SFG leaked thread local into outer interceptor after proceed")
        }
    }

    // Regression test for KTOR-2644.
    @Test
    fun `thread context is set inside withContext after cross-thread resume`() {
        val threadLocal = ThreadLocal<String?>()
        val phase = PipelinePhase("test")
        val dispatcher = ExternalEventLoopDispatcher()

        dispatcher.runEventLoop {
            val loopThread = Thread.currentThread()
            var resumedThread: Thread? = null

            val pipeline = Pipeline<Unit, Unit>(phase)
            var capturedValue: String? = null

            pipeline.intercept(phase) {
                withContext(threadLocal.asContextElement("set")) {
                    proceed()
                    capturedValue = threadLocal.get()
                    resumedThread = Thread.currentThread()
                }
            }

            pipeline.intercept(phase) {
                yieldToAnotherThread()
            }

            pipeline.execute(Unit, Unit)

            assertNull(threadLocal.get(), "SFG leaked thread local after pipeline")
            assertEquals("set", capturedValue, "Thread local not set inside withContext after cross-thread resume")
            assertSame(loopThread, resumedThread, "proceed() returned on wrong thread after cross-thread suspension")
        }
    }
}

// Suspends the coroutine unintercepted and resumes it from a raw background thread.
// This ensures SFG.continuation.resumeWith is called from a non-dispatcher thread,
// triggering the isDispatchNeeded=true branch in resumeRootWith.
private suspend fun yieldToAnotherThread(): Unit = suspendCoroutineUninterceptedOrReturn { cont ->
    Thread { cont.resumeWith(Result.success(Unit)) }.start()
    COROUTINE_SUSPENDED
}

// Mimics a Netty/Vert.x event loop: isDispatchNeeded=false on the loop thread, but NOT a
// kotlinx.coroutines EventLoopImplBase, so executeUnconfined runs continuations inline.
private class ExternalEventLoopDispatcher : CoroutineDispatcher() {
    private val onLoopThread = ThreadLocal.withInitial { false }
    private val queue = LinkedBlockingQueue<Runnable>()

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = !onLoopThread.get()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.put(block)
    }

    fun runEventLoop(block: suspend () -> Unit) {
        onLoopThread.set(true)
        var failure: Throwable? = null
        val done = Runnable {}
        try {
            block.startCoroutine(
                Continuation(this) { result ->
                    failure = result.exceptionOrNull()
                    queue.put(done)
                }
            )
            while (true) {
                val task = queue.take()
                if (task === done) break
                task.run()
            }
            failure?.let { throw it }
        } finally {
            onLoopThread.set(false)
        }
    }
}
