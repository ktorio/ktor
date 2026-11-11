/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * An unbounded FIFO queue for acquiring tokens from a [rateLimiter].
 *
 * Waiting callers retain their own coroutines; the queue does not execute their work.
 * Only the first waiter attempts consumption, so later arrivals cannot overtake it.
 *
 * @param rateLimiter the limiter that supplies tokens.
 * @param maxWaitTime maximum duration of one acquisition, including time waiting behind other callers.
 * Zero or negative values attempt consumption once without waiting for tokens.
 */
@InternalAPI
public class RateLimiterQueue(
    private val rateLimiter: RateLimiter,
    private val maxWaitTime: Duration
) {
    private val mutex = Mutex()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()
    private val closed = CompletableDeferred<Unit>()

    /**
     * Consumes [tokens], suspending until this acquisition reaches the front and enough tokens are available.
     *
     * Cancelling the caller removes its acquisition from the queue.
     * Consumed tokens are replenished by the limiter and are not returned when the caller finishes its work.
     * When the wait time is zero or negative, attempts immediate admission without queuing or overtaking waiters.
     * Token and FIFO waiting count toward the same maximum wait time.
     *
     * @param tokens the positive number of tokens to consume.
     * @param maxWaitTime overrides this queue's default waiting budget for this acquisition only.
     * @throws RateLimitRejectedException if tokens are unavailable and waiting is disabled, or the wait expires.
     * @throws CancellationException if the caller is cancelled or the queue is closed.
     */
    public suspend fun acquire(
        tokens: Int = 1,
        maxWaitTime: Duration = this.maxWaitTime
    ) {
        require(tokens > 0) { "Token count must be positive, but was $tokens" }
        val acquisition = Acquisition()
        if (maxWaitTime <= Duration.ZERO) {
            currentCoroutineContext().ensureActive()
            ensureOpen()
            if (tryConsume(tokens, acquisition, rejectIfQueued = true) is RateLimiter.State.Exhausted) {
                throw RateLimitRejectedException(maxWaitTime, rejectionMessage(acquisition.reason, maxWaitTime))
            }
            return
        }
        val acquired = withTimeoutOrNull(maxWaitTime) {
            awaitTokens(tokens, acquisition)
            true
        }
        if (acquired == null) {
            throw RateLimitRejectedException(maxWaitTime, rejectionMessage(acquisition.reason, maxWaitTime))
        }
    }

    private suspend fun awaitTokens(tokens: Int, acquisition: Acquisition) {
        val turn = CompletableDeferred<Unit>()
        mutex.withLock {
            ensureOpen()
            waiters.addLast(turn)
            if (waiters.size == 1) turn.complete(Unit)
        }

        try {
            select<Unit> {
                closed.onAwait { throw CancellationException("Rate limiter queue is closed") }
                turn.onAwait {}
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                ensureOpen()
                when (val state = tryConsume(tokens, acquisition)) {
                    is RateLimiter.State.Available -> return
                    is RateLimiter.State.Exhausted -> awaitRefill(state.toWait)
                }
            }
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    waiters.remove(turn)
                    waiters.firstOrNull()?.complete(Unit)
                }
            }
        }
    }

    private suspend fun tryConsume(
        tokens: Int,
        acquisition: Acquisition,
        rejectIfQueued: Boolean = false
    ): RateLimiter.State = mutex.withLock {
        ensureOpen()
        if (rejectIfQueued && waiters.isNotEmpty()) return@withLock RateLimiter.State.Exhausted(Duration.ZERO)
        acquisition.reason = "tokens exhausted"
        rateLimiter.tryConsume(tokens)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun awaitRefill(duration: Duration) {
        select<Unit> {
            closed.onAwait { throw CancellationException("Rate limiter queue is closed") }
            onTimeout(duration.coerceAtLeast(1.milliseconds)) {}
        }
    }

    private fun ensureOpen() {
        if (closed.isCompleted) throw CancellationException("Rate limiter queue is closed")
    }

    /**
     * Rejects future acquisitions and cancels waiting acquisitions without closing the underlying limiter.
     */
    public fun close() {
        closed.complete(Unit)
    }

    private class Acquisition {
        var reason: String = "requests queued ahead"
    }
}

private fun rejectionMessage(reason: String, maxWaitTime: Duration): String {
    val details = if (maxWaitTime > Duration.ZERO) "timed out after $maxWaitTime waiting" else "no wait allowed"
    return "$reason; $details"
}

/**
 * Rejects a request because tokens are unavailable and waiting is disabled, or the maximum wait time expires.
 *
 * @property maxWaitTime the configured maximum duration of an acquisition.
 * @param message a rejection description, optionally including context supplied by the caller.
 */
public class RateLimitRejectedException(
    public val maxWaitTime: Duration,
    message: String = rejectionMessage("tokens exhausted", maxWaitTime)
) : CancellationException(message)
