/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.*
import kotlinx.coroutines.sync.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared cooldown deadlines independent of token buckets and admission queues.
 * Callers with equal keys observe the same deadline; unrelated keys proceed independently.
 */
@InternalAPI
public class RateLimitCooldown {
    private val mutex = Mutex()
    private val deadlines = mutableMapOf<Any, ComparableTimeMark>()
    private val closed = CompletableDeferred<Unit>()

    /**
     * Postpones admission for [key] by at least [duration] from now, extending any existing deadline.
     * Non-positive durations and updates after closing are ignored.
     */
    public suspend fun postpone(key: Any, duration: Duration) {
        if (duration <= Duration.ZERO || closed.isCompleted) return
        val deadline: ComparableTimeMark = TimeSource.Monotonic.markNow() + duration
        mutex.withLock {
            if (closed.isCompleted) return@withLock
            val current = deadlines[key]
            if (current == null || deadline > current) deadlines[key] = deadline
        }
    }

    /**
     * Waits for the current deadline for [key], checking extensions before returning.
     * Waiting continues until the deadline expires or the caller is cancelled.
     *
     * @throws CancellationException if the caller is cancelled or this gate is closed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    public suspend fun await(key: Any) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val duration = remaining(key)
            if (duration <= Duration.ZERO) return
            select<Unit> {
                closed.onAwait { throw CancellationException("Rate limit cooldown gate is closed") }
                onTimeout(duration.coerceAtLeast(1.milliseconds)) {}
            }
        }
    }

    private suspend fun remaining(key: Any): Duration = mutex.withLock {
        if (closed.isCompleted) throw CancellationException("Rate limit cooldown gate is closed")
        val deadline = deadlines[key] ?: return@withLock Duration.ZERO
        val duration = -deadline.elapsedNow()
        if (duration <= Duration.ZERO) deadlines.remove(key)
        duration.coerceAtLeast(Duration.ZERO)
    }

    /**
     * Cancels waiting callers and rejects future admission.
     */
    public fun close() {
        closed.complete(Unit)
    }
}
