/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.util.date.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.delay
import kotlin.time.*

@OptIn(ExperimentalTime::class)
private fun (() -> Long).asClock(): Clock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(this@asClock())
}

private fun (() -> Long).asTimeSource(): TimeSource.WithComparableMarks =
    object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
        override fun read(): Long = this@asTimeSource()
    }

private class LimiterState(val tokens: Int, val lastRefillMark: ComparableTimeMark)

internal class DefaultRateLimiter @OptIn(ExperimentalTime::class) constructor(
    private val limit: Int,
    private val refillPeriod: Duration,
    private val clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : RateLimiter {

    @OptIn(ExperimentalTime::class)
    constructor(limit: Int,
                refillPeriod: Duration,
                clock: () -> Long = ::getTimeMillis) : this(limit, refillPeriod, clock.asClock(), clock.asTimeSource())

    private val state = atomic(LimiterState(limit, timeSource.markNow()))

    @OptIn(ExperimentalTime::class)
    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        while (true) {
            val current = state.value
            val now = timeSource.markNow()
            val base = if (now - current.lastRefillMark >= refillPeriod) LimiterState(limit, now) else current
            val remaining = (refillPeriod - (now - base.lastRefillMark)).coerceAtLeast(Duration.ZERO)
            val newTokens = base.tokens - tokens
            if (newTokens < 0) return RateLimiter.State.Exhausted(remaining)

            if (state.compareAndSet(current, LimiterState(newTokens, base.lastRefillMark))) {
                val nextRefill = clock.now() + remaining
                return RateLimiter.State.Available(newTokens, limit, nextRefill.toEpochMilliseconds())
            }
        }
    }
}

internal class RateLimiterIncrementalBucket @OptIn(ExperimentalTime::class) constructor(
    private val limit: Int,
    private val refillRate: Duration,
    private val clock: Clock,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
): RateLimiter {

    @OptIn(ExperimentalTime::class)
    constructor(limit: Int,
                refillRate: Duration,
                clock: () -> Long = ::getTimeMillis) : this(limit, refillRate, clock.asClock(), clock.asTimeSource())

//    private val currentTokens = atomic(limit)
//    private var lastRefillMark = timeSource.markNow()

    private val state = atomic(LimiterState(limit, timeSource.markNow()))

    @OptIn(ExperimentalTime::class)
    override suspend fun tryConsume(tokens: Int): RateLimiter.State {
        while (true) {
            val current = state.value
            val now = timeSource.markNow()
            val base = refilled(current, now)
            val sinceLastToken = now - base.lastRefillMark
            val newTokens = base.tokens - tokens

            if (newTokens < 0) {
                val wait = refillRate * (-newTokens) - sinceLastToken
                return RateLimiter.State.Exhausted(wait.coerceAtLeast(Duration.ZERO))
            }

            if (state.compareAndSet(current, LimiterState(newTokens, base.lastRefillMark))) {
                val untilFull = (refillRate * (limit - newTokens) - sinceLastToken).coerceAtLeast(Duration.ZERO)
                val fullAt = clock.now() + untilFull
                return RateLimiter.State.Available(newTokens, limit, fullAt.toEpochMilliseconds())
            }
        }
    }

    private fun refilled(s: LimiterState, now: ComparableTimeMark): LimiterState {
        if (s.tokens >= limit) return LimiterState(limit, now)   // a full bucket doesn't accumulate progress

        val added = (now - s.lastRefillMark).inWholeNanoseconds / refillRate.inWholeNanoseconds
        if (added <= 0) return s

        val tokens = s.tokens + added
        return if (tokens >= limit) {
            LimiterState(limit, now)
        } else {
            // advance by whole periods only, so partial progress toward the next token is kept
            LimiterState(tokens.toInt(), s.lastRefillMark + refillRate * added.toInt())
        }
    }
}
