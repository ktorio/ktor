package org.jetbrains.ktor.pipeline

import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

open class SuspendQueue<T : Any>(initialSize: Int) {
    private val lock = ReentrantLock()
    private val queue = ArrayDeque<T>(initialSize)
    private var continuation: Continuation<T?>? = null
    private var endOfStream = false

    @Volatile
    private var cancellation: Throwable? = null

    fun push(element: T, lastElement: Boolean) {
        val cont = lock.withLock {
            check(!endOfStream) { "There should be no elements after last element, but we've got $element" }
            onPush(element)
            if (lastElement)
                endOfStream = true

            val cont = continuation
            if (cont == null) {
                queue.offer(element)
                null
            } else {
                continuation = null
                cont
            }
        }
        cont?.also { onPull(element) }?.resume(element)
    }

    suspend fun pull(): T? {
        cancellation?.let { throw it }

        return suspendCoroutineOrReturn {
            lock.withLock {
                val element: T? = queue.poll()
                when {
                    element != null -> {
                        onPull(element)
                        element
                    }
                    endOfStream -> null
                    else -> {
                        onPull(null)
                        check(continuation == null)
                        continuation = it
                        COROUTINE_SUSPENDED
                    }
                }
            }
        }
    }

    fun clear(action: (T) -> Unit) {
        lock.withLock {
            queue.forEach(action)
            queue.clear()
        }
    }

    fun cancel(t: Throwable? = null) {
        lock.withLock {
            val cause = cancellation ?: t ?: CancellationException()
            if (t != null && t !== cause) {
                cause.addSuppressed(t)
            }
            cancellation = cause

            val c = continuation
            continuation = null

            c?.resumeWithException(cause)
        }
    }

    open fun onPush(element: T) {}
    open fun onPull(element: T?) {}

    fun isNotEmpty(): Boolean = lock.withLock { queue.isNotEmpty() }
    fun isEmpty(): Boolean = lock.withLock { queue.isEmpty() }
}