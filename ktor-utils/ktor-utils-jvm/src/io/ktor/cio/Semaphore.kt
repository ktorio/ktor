package io.ktor.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.*
import java.util.*

/**
 * Asynchronous Semaphore.
 */
class Semaphore(val limit: Int) {
    private val mutex = Mutex()
    private var visitors = 0
    private val waiters: Queue<CancellableContinuation<Unit>> = LinkedList()

    init {
        check(limit > 0) { "Semaphore limit should be > 0" }
    }

    /**
     * Enters the semaphore.
     *
     * If the number of visitors didn't reach [limit], this function will return immediately.
     * If the limit is reached, it will wait until [leave] is call from other coroutine.
     */
    suspend fun enter() {
        while (true) {
            mutex.lock()
            if (visitors < limit) {
                ++visitors
                mutex.unlock()
                return
            }

            suspendCancellableCoroutine<Unit> {
                waiters.offer(it)
                mutex.unlock()
            }
        }
    }

    /**
     * Exits the semaphore.
     *
     * If [limit] was reached, this will potentially resume
     * suspended coroutines that invoked the [enter] method.
     */
    fun leave() {
        runBlocking { mutex.lock() }
        if (visitors == 0) {
            mutex.unlock()
            throw IllegalStateException("Semaphore is empty")
        }

        visitors--
        val waiter = waiters.poll()
        mutex.unlock()
        waiter?.resume(Unit)
    }
}
