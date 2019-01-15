package io.ktor.util.cio

import io.ktor.util.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.coroutines.*

/**
 * Asynchronous Semaphore.
 * @property limit is the semaphores permits count limit
 */
@InternalAPI
class Semaphore(val limit: Int) {
    private val permits = atomic(limit)
    private val waiters = ConcurrentHashMap<CancellableContinuation<Unit>, Unit>()

    init {
        check(limit > 0) { "Semaphore limit should be > 0" }
    }

    /**
     * Enters the semaphore.
     *
     * If the number of permits didn't reach [limit], this function will return immediately.
     * If the limit is reached, it will wait until [leave] is call from other coroutine.
     */
    suspend fun enter() {
        while (true) {
            val current = permits.value
            if (current > 0 && permits.compareAndSet(current, current - 1)) return

            suspendCancellableCoroutine<Unit> {
                waiters[it] = Unit

                val newValue = permits.value
                if (newValue > 0 && waiters.remove(it) != null) it.resume(Unit)
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
        var value = permits.incrementAndGet()

        while (value > 0 && waiters.isNotEmpty()) {
            val key = waiters.keys().nextElement() ?: continue
            waiters.remove(key) ?: continue

            key.resume(Unit)
            value = permits.value
        }
    }
}
