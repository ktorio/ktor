package org.jetbrains.ktor.pipeline

import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

open class SuspendQueue<T : Any>(initialSize: Int) : Iterable<T> {
    internal val lock = ReentrantLock()
    protected val queue = ArrayDeque<T>(initialSize)
    protected var continuation: Continuation<T?>? = null
    protected var endOfStream = false

    fun push(element: T, lastElement: Boolean) {
        check(!endOfStream)
        val cont = lock.withLock {
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

    @Deprecated("Isn't it dangerous to iterate over concurrently accessed queue with no lock?")
    override fun iterator(): Iterator<T> = queue.iterator()

    open fun onPush(element: T) {}

    open fun onPull(element: T) {}
    fun isNotEmpty(): Boolean = lock.withLock { queue.isNotEmpty() }
    fun isEmpty(): Boolean = lock.withLock { queue.isEmpty() }

}