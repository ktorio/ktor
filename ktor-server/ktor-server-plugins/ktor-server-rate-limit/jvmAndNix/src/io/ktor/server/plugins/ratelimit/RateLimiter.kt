/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.util.date.*
import kotlin.jvm.*
import kotlin.time.*

/**
 * An interface for rate limiters.
 */
public interface RateLimiter {

    /**
     * Tries to consume the [tokens] amount of tokens and returns state of the rate limiter.
     */
    public suspend fun tryConsume(tokens: Int = 1): State

    /**
     * State of the rate limiter.
     */
    public sealed class State {
        /**
         * Rate limiter has enough tokens.
         */
        public class Available(
            public val remainingTokens: Int,
            public val limit: Int,
            public val refillAtTimeMillis: Long
        ) : State()

        /**
         * Rate limiter is exhausted.
         */
        public class Exhausted(public val toWait: Duration) : State()
    }

    public companion object {
        /**
         * An implementation of [RateLimiter] that always has enough tokens and will never be refreshed
         */
        public val Unlimited: RateLimiter = object : RateLimiter {
            override suspend fun tryConsume(tokens: Int): State =
                State.Available(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE)
        }

        /**
         * An implementation of [RateLimiter] that starts with [initialSize] tokens,
         * and will be refreshed to [limit] tokens every [refillPeriod]
         */
        public fun default(
            limit: Int,
            refillPeriod: Duration,
            initialSize: Int = limit,
            clock: () -> Long = ::getTimeMillis
        ): RateLimiter {
            return DefaultRateLimiter(limit, refillPeriod, initialSize, clock)
        }
    }
}
