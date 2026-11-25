/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
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
 * Limiter calls run outside the queue mutex. Custom limiters must cooperate with coroutine cancellation.
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
     * When the wait time is zero or negative, attempts admission without waiting for a queue position or tokens.
     * Existing waiters cannot be overtaken. A custom limiter call can still have its own execution latency.
     * Token and FIFO waiting count toward the same maximum wait time.
     *
     * @param tokens the number of tokens to consume, passed unchanged to the limiter.
     * @param maxWaitTime overrides this queue's default waiting budget for this acquisition only.
     * @throws RateLimitRejectedException if the acquisition must wait but waiting is disabled, or if the wait expires.
     * @throws CancellationException if the caller is cancelled or the queue is closed.
     */
    public suspend fun acquire(
        tokens: Int = 1,
        maxWaitTime: Duration = this.maxWaitTime
    ): Unit = coroutineScope {
        val acquisition = Acquisition()
        val acquisitionJob = currentCoroutineContext().job
        val closeHandle = closed.invokeOnCompletion {
            acquisitionJob.cancel(CancellationException("Rate limiter queue is closed"))
        }
        try {
            currentCoroutineContext().ensureActive()
            ensureOpen()
            if (maxWaitTime <= Duration.ZERO) {
                awaitTokens(tokens, acquisition, maxWaitTime)
                return@coroutineScope
            }
            val acquired = withTimeoutOrNull(maxWaitTime) {
                awaitTokens(tokens, acquisition, maxWaitTime)
                true
            }
            if (acquired == null) {
                throw RateLimitRejectedException(maxWaitTime, rejectionMessage(acquisition.reason, maxWaitTime))
            }
        } finally {
            closeHandle.dispose()
        }
    }

    private suspend fun awaitTokens(tokens: Int, acquisition: Acquisition, maxWaitTime: Duration) {
        val turn = CompletableDeferred<Unit>()
        var enqueued = false
        try {
            val first = if (maxWaitTime <= Duration.ZERO) {
                if (!mutex.tryLock()) {
                    throw RateLimitRejectedException(maxWaitTime, rejectionMessage(acquisition.reason, maxWaitTime))
                }
                try {
                    ensureOpen()
                    if (waiters.isNotEmpty()) {
                        throw RateLimitRejectedException(maxWaitTime, rejectionMessage(acquisition.reason, maxWaitTime))
                    }
                    waiters.addLast(turn)
                    enqueued = true
                } finally {
                    mutex.unlock()
                }
                true
            } else {
                mutex.withLock {
                    ensureOpen()
                    waiters.addLast(turn)
                    enqueued = true
                    waiters.size == 1
                }
            }
            if (first) turn.complete(Unit)
            select<Unit> {
                closed.onAwait { throw CancellationException("Rate limiter queue is closed") }
                turn.onAwait {}
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                ensureOpen()
                acquisition.reason = "tokens exhausted"
                when (val state = rateLimiter.tryConsume(tokens)) {
                    is RateLimiter.State.Available -> return

                    is RateLimiter.State.Exhausted -> {
                        if (maxWaitTime <= Duration.ZERO) {
                            throw RateLimitRejectedException(
                                maxWaitTime,
                                rejectionMessage(acquisition.reason, maxWaitTime)
                            )
                        }
                        awaitRefill(state.toWait)
                    }
                }
            }
        } finally {
            if (enqueued) {
                withContext(NonCancellable) {
                    val next = mutex.withLock {
                        val wasFirst = waiters.firstOrNull() === turn
                        waiters.remove(turn)
                        if (wasFirst) waiters.firstOrNull() else null
                    }
                    next?.complete(Unit)
                }
            }
        }
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
     * Rejects future acquisitions and cancels active acquisitions, including suspending limiter calls,
     * without closing the underlying limiter. Custom limiters must cooperate with cancellation.
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
 * Indicates that a request could not proceed within its allowed wait time.
 *
 * @property maxWaitTime the configured maximum duration of an acquisition.
 * @param message a rejection description, optionally including context supplied by the caller.
 */
public class RateLimitRejectedException(
    public val maxWaitTime: Duration,
    message: String = rejectionMessage("tokens exhausted", maxWaitTime)
) : IllegalStateException(message)
