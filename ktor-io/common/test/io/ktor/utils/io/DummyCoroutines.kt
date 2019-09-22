package io.ktor.utils.io

import kotlin.coroutines.*

class DummyCoroutines {
    private var failure: Throwable? = null
    private val queue = ArrayList<Task<*>>()
    private var liveCoroutines = 0

    private inner class Completion : Continuation<Unit> {
        override val context: CoroutineContext
            get() = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            liveCoroutines--
            failure = failure ?: result.exceptionOrNull()
            if (result.isSuccess) {
                process()
            }
        }
    }

    private val completion = Completion()

    fun schedule(c: Continuation<Unit>) {
        ensureNotFailed()
        liveCoroutines++
        queue += Task.Resume(c, Unit)
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

        while (queue.isNotEmpty()) {
            queue.removeAt(0).run()
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
