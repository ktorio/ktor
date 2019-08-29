/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
@Deprecated(
    "Ktor Semaphore is deprecated. Consider using kotlinx.coroutines.sync.Semaphore instead.",
    ReplaceWith("kotlinx.coroutines.sync.Semaphore", ""),
    DeprecationLevel.WARNING
)
class Semaphore(val limit: Int) {
    private val permits = atomic(limit)
    private val waiters = ConcurrentLinkedQueue<CancellableContinuation<Unit>>()

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
            if (permits.value > 0) continue

            suspendCancellableCoroutine<Unit> {
                waiters.add(it)

                val newValue = permits.value
                if (newValue > 0) {
                    waiters.poll()?.resume(Unit)
                }
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
        check(value <= limit)

        while (value > 0 && waiters.isNotEmpty()) {
            waiters.poll()?.resume(Unit)
            value = permits.value
        }
    }
}
