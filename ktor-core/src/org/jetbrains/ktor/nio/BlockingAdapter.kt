package org.jetbrains.ktor.nio

import java.util.concurrent.*

/**
 * Very similar to CompletableFuture.asHandler but can be used multiple times
 */
class BlockingAdapter {
    private val semaphore = Semaphore(0)
    private var error: Throwable? = null
    private var count: Int = -1

    val handler = object : AsyncHandler {
        override fun success(count: Int) {
            error = null
            this@BlockingAdapter.count = count
            semaphore.release()
        }

        override fun successEnd() {
            count = -1
            error = null
            semaphore.release()
        }

        override fun failed(cause: Throwable) {
            error = cause
            semaphore.release()
        }
    }

    fun await(): Int {
        semaphore.acquire()
        error?.let { throw it }
        return count
    }
}