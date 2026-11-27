/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

import io.ktor.util.*
import io.ktor.utils.io.InternalAPI
import kotlin.time.*

/**
 * An interface for rate limiters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter)
 */
public interface RateLimiter {

    /**
     * Tries to consume [tokens] and returns the state of the rate limiter.
     * The token count must be non-negative. A count of zero consumes no tokens.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.tryConsume)
     *
     * @throws IllegalArgumentException if a built-in limiter receives a negative token count.
     */
    public suspend fun tryConsume(tokens: Int = 1): State

    /**
     * Returns the current quota, accounting for elapsed refills without consuming tokens.
     *
     * Concurrent requests can change the quota immediately after the snapshot is taken.
     * Implementations that support quota inspection should override this method.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.snapshot)
     *
     * @return a snapshot of the remaining tokens and current limit.
     * @throws UnsupportedOperationException if this limiter does not support quota inspection.
     */
    public suspend fun snapshot(): RateLimiterSnapshot {
        throw UnsupportedOperationException("This rate limiter does not support quota inspection")
    }

    /**
     * State of the rate limiter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.State)
     */
    public sealed class State {
        /**
         * Rate limiter has enough tokens.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.State.Available)
         */
        public class Available(
            public val remainingTokens: Int,
            public val limit: Int,
            public val refillAtTimeMillis: Long
        ) : State()

        /**
         * Rate limiter is exhausted.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.State.Exhausted)
         */
        public class Exhausted(public val toWait: Duration) : State()
    }

    public companion object {
        /**
         * An implementation of [RateLimiter] that always has enough tokens and will never be refreshed
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.Companion.Unlimited)
         */
        public val Unlimited: RateLimiter = object : RateLimiter {
            override suspend fun snapshot(): RateLimiterSnapshot = RateLimiterSnapshot(Int.MAX_VALUE, Int.MAX_VALUE)

            override suspend fun tryConsume(tokens: Int): State {
                require(tokens >= 0) { "Token count must be non-negative, got $tokens" }
                return State.Available(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE)
            }
        }

        /**
         * Creates a limiter for the legacy server API using a millisecond callback for refill timing and headers.
         *
         * [refillPeriod] must be positive and finite.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiter.Companion.default)
         */
        @InternalAPI
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            clock: () -> Long
        ): RateLimiter {
            return DefaultRateLimiter(limit, refillPeriod, clock)
        }

        /**
         * Creates a limiter with [limit] tokens, fully refilled every positive, finite [refillPeriod].
         * Uses the system clock for reset timestamps and [timeSource] for refill timing.
         */
        @OptIn(ExperimentalTime::class)
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
        ): RateLimiter = default(limit, refillPeriod, Clock.System, timeSource)

        /**
         * Creates a rate limiter with [limit] tokens that is fully refilled every [refillPeriod].
         *
         * Refill timing uses [timeSource]. The [clock] supplies epoch timestamps for reset headers.
         * [refillPeriod] must be positive and finite.
         */
        @OptIn(ExperimentalTime::class)
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            clock: Clock,
            timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
        ): RateLimiter {
            return DefaultRateLimiter(limit, refillPeriod, clock, timeSource)
        }

        /**
         * Creates an initially full limiter with [limit] tokens, replenishing one token every positive,
         * finite [refillInterval]. Uses the system clock for reset timestamps and [timeSource] for refills.
         */
        @OptIn(ExperimentalTime::class)
        public fun incrementalRefill(
            limit: Int,
            refillInterval: Duration,
            timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
        ): RateLimiter = incrementalRefill(limit, refillInterval, Clock.System, timeSource)

        /**
         * Creates a rate limiter with [limit] tokens that replenishes one token every [refillInterval].
         * [refillInterval] must be positive and finite.
         *
         * Refill timing uses [timeSource]. The [clock] supplies epoch timestamps for reset headers.
         */
        @OptIn(ExperimentalTime::class)
        public fun incrementalRefill(
            limit: Int,
            refillInterval: Duration,
            clock: Clock,
            timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
        ): RateLimiter {
            return IncrementalRefillRateLimiter(limit, refillInterval, clock, timeSource)
        }
    }
}

/**
 * A snapshot of a rate limiter's quota.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.ratelimit.RateLimiterSnapshot)
 *
 * @property remainingTokens the number of tokens currently available for consumption.
 * @property limit the maximum number of tokens the limiter can hold at the time of the snapshot.
 */
public class RateLimiterSnapshot(
    public val remainingTokens: Int,
    public val limit: Int
)
