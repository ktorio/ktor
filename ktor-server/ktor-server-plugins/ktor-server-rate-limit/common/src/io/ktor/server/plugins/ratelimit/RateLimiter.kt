/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import kotlin.time.*

/**
 * An interface for rate limiters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter)
 */
public interface RateLimiter {

    /**
     * Tries to consume the [tokens] amount of tokens and returns state of the rate limiter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.tryConsume)
     */
    public suspend fun tryConsume(tokens: Int = 1): State

    /**
     * Returns the current quota, accounting for elapsed refills without consuming tokens.
     *
     * Concurrent requests can change the quota immediately after the snapshot is taken.
     * Implementations that support quota inspection should override this method.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.state)
     *
     * @return a snapshot of the remaining tokens and current limit.
     * @throws UnsupportedOperationException if this limiter does not support quota inspection.
     */
    public suspend fun state(): RateLimiterSnapshot {
        throw UnsupportedOperationException("This rate limiter does not support quota inspection")
    }

    /**
     * State of the rate limiter.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.State)
     */
    public sealed class State {
        /**
         * Rate limiter has enough tokens.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.State.Available)
         */
        public class Available(
            public val remainingTokens: Int,
            public val limit: Int,
            public val refillAtTimeMillis: Long
        ) : State()

        /**
         * Rate limiter is exhausted.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.State.Exhausted)
         */
        public class Exhausted(public val toWait: Duration) : State()
    }

    public companion object {
        /**
         * An implementation of [RateLimiter] that always has enough tokens and will never be refreshed
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.Companion.Unlimited)
         */
        public val Unlimited: RateLimiter = io.ktor.ratelimit.RateLimiter.Unlimited.asServerRateLimiter()

        /**
         * An implementation of [RateLimiter] that starts with [limit] tokens,
         * and will be refilled every [refillPeriod].
         *
         * Note: [initialSize] parameter is ignored and will be removed in the future.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.Companion.default)
         */
        @Deprecated("Use the overload accepting kotlin.time.Clock.")
        @Suppress("DEPRECATION")
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            @Suppress("UNUSED_PARAMETER") initialSize: Int = limit,
            clock: () -> Long
        ): RateLimiter {
            return io.ktor.ratelimit.RateLimiter.default(limit, refillPeriod, initialSize, clock).asServerRateLimiter()
        }

        /**
         * Creates a rate limiter with [limit] tokens that is fully refilled every [refillPeriod].
         *
         * Refill timing uses monotonic time. The [clock] supplies epoch timestamps for reset headers.
         * The [initialSize] parameter is ignored.
         */
        @OptIn(ExperimentalTime::class)
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            @Suppress("UNUSED_PARAMETER") initialSize: Int = limit,
            clock: Clock = Clock.System
        ): RateLimiter {
            return io.ktor.ratelimit.RateLimiter.default(limit, refillPeriod, initialSize, clock).asServerRateLimiter()
        }

        @Deprecated("Use the overload accepting kotlin.time.Clock.")
        @Suppress("DEPRECATION")
        public fun incrementalRefill(
            limit: Int,
            refillInterval: Duration,
            clock: () -> Long
        ): RateLimiter {
            return io.ktor.ratelimit.RateLimiter.incrementalRefill(limit, refillInterval, clock).asServerRateLimiter()
        }

        /**
         * Creates a rate limiter with [limit] tokens that replenishes one token every [refillInterval].
         *
         * Refill timing uses monotonic time. The [clock] supplies epoch timestamps for reset headers.
         */
        @OptIn(ExperimentalTime::class)
        public fun incrementalRefill(
            limit: Int,
            refillInterval: Duration,
            clock: Clock = Clock.System
        ): RateLimiter {
            return io.ktor.ratelimit.RateLimiter.incrementalRefill(limit, refillInterval, clock).asServerRateLimiter()
        }
    }
}

/**
 * A snapshot of a rate limiter's quota.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiterSnapshot)
 *
 * @property remainingTokens the number of tokens currently available for consumption.
 * @property limit the maximum number of tokens the limiter can hold at the time of the snapshot.
 */
public class RateLimiterSnapshot(
    public val remainingTokens: Int,
    public val limit: Int
)
