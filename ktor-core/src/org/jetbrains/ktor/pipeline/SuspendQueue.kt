package org.jetbrains.ktor.pipeline

import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

open class SuspendQueue<T : Any>(initialSize: Int) : Iterable<T> {
    val lock = ReentrantLock()
    val queue = ArrayDeque<T>(initialSize)
    var continuation: Continuation<T>? = null
    var endOfStream = false

    fun finish() = lock.withLock {
        endOfStream = true
    }

    fun push(element: T) {
        check(!endOfStream)
        val cont = lock.withLock {
            onPush(element)
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
        lock.withLock {
            val element: T? = queue.poll()
            if (element != null) {
                onPull(element)
                return element
            }
        }
        if (endOfStream)
            return null

        return suspendCoroutineOrReturn {
            check(continuation == null)
            continuation = it
            SUSPENDED_MARKER
        }
    }

    fun clear(action: (T) -> Unit) {
        lock.withLock {
            queue.forEach(action)
            queue.clear()
        }
    }

    override fun iterator(): Iterator<T> = queue.iterator()

    open fun onPush(element: T) {}

    open fun onPull(element: T) {}
    fun isNotEmpty(): Boolean = lock.withLock { queue.isNotEmpty() }
    fun isEmpty(): Boolean = lock.withLock { queue.isEmpty() }

}