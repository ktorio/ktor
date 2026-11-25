/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.ratelimit.*
import io.ktor.util.date.*
import io.ktor.utils.io.InternalAPI
import kotlin.time.*

/**
 * An interface for rate limiters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter)
 */
@Deprecated(
    "Use io.ktor.ratelimit.RateLimiter, shared by the client and server plugins.",
    ReplaceWith("RateLimiter", "io.ktor.ratelimit.RateLimiter")
)
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
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.snapshot)
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
        @Deprecated("Use io.ktor.ratelimit.RateLimiter.Unlimited.")
        public val Unlimited: RateLimiter = io.ktor.ratelimit.RateLimiter.Unlimited.asServerRateLimiter()

        /**
         * An implementation of [RateLimiter] that starts with [limit] tokens,
         * and will be refilled every [refillPeriod].
         *
         * Note: [initialSize] parameter is ignored and will be removed in the future.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimiter.Companion.default)
         */
        @OptIn(InternalAPI::class)
        @Deprecated("Use io.ktor.ratelimit.RateLimiter.default with kotlin.time.Clock.")
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            @Suppress("UNUSED_PARAMETER") initialSize: Int = limit,
            clock: () -> Long = ::getTimeMillis
        ): RateLimiter {
            return io.ktor.ratelimit.RateLimiter.default(limit, refillPeriod, clock).asServerRateLimiter()
        }
    }
}
