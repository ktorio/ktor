package io.ktor.client.cio

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.sync.*
import java.util.*

class Semaphore(val limit: Int) {
    private val mutex = Mutex()
    private var visitors = 0
    private val waiters: Queue<CancellableContinuation<Unit>> = LinkedList()

    init {
        assert(limit > 0) { "Semaphore limit should be > 0" }
    }

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
