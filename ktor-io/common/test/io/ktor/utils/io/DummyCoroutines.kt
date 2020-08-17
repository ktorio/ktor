package io.ktor.utils.io

import kotlinx.coroutines.*
import kotlin.coroutines.*

class DummyCoroutines : ContinuationInterceptor, @OptIn(InternalCoroutinesApi::class) Delay {
    private var failure: Throwable? = null
    private val queue = ArrayList<Task<*>>()
    private val timeoutQueue = ArrayList<Task<*>>()
    private var liveCoroutines = 0

    private inner class Completion : Continuation<Unit> {
        override val context: CoroutineContext
            get() = this@DummyCoroutines

        override fun resumeWith(result: Result<Unit>) {
            liveCoroutines--
            failure = failure ?: result.exceptionOrNull()
            if (result.isSuccess) {
                process()
            }
        }
    }

    override val key: CoroutineContext.Key<*>
        get() = ContinuationInterceptor.Key

    private val completion = Completion()

    fun schedule(c: Continuation<Unit>) {
        ensureNotFailed()
        liveCoroutines++
        queue += Task.Resume(c, Unit)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        timeoutQueue += Task.Resume(continuation, Unit)
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        val task = Task.Resume(Continuation(this@DummyCoroutines, { block.run() }), Unit)
        timeoutQueue += task
        return DisposableHandle {
            timeoutQueue.remove(task)
        }
    }

    fun schedule(block: suspend () -> Unit) {
        schedule(block.createCoroutine(completion))
    }

    suspend fun yield() {
        return suspendCoroutine { c ->
            ensureNotFailed()
            queue += Task.Resume(c, Unit)
        }
    }

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return continuation
    }

    fun run() {
        if (liveCoroutines == 0) throw IllegalStateException("No coroutines has been scheduled")
        ensureNotFailed()

        process()
        failure?.let { throw it }
        if (liveCoroutines > 0) {
            throw IllegalStateException("There are suspended coroutines remaining: $liveCoroutines")
        }
    }

    private fun process() {
        ensureNotFailed()

        while (queue.isNotEmpty() || timeoutQueue.isNotEmpty()) {
            if (queue.isNotEmpty()) {
                queue.removeAt(0).run()
            } else if (timeoutQueue.isNotEmpty()) {
                timeoutQueue.removeAt(0).run()
            }
            ensureNotFailed()
        }
    }

    private fun ensureNotFailed() {
        failure?.let { throw it }
    }

    private sealed class Task<T>(val c: Continuation<T>) {
        abstract fun run()

        class Resume<T>(c: Continuation<T>, val value: T) : Task<T>(c) {
            override fun run() {
                c.resume(value)
            }
        }
    }
}
