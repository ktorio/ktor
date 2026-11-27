/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.request.*
import io.ktor.ratelimit.*
import io.ktor.utils.io.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/**
 * Configures a client rate limit provider.
 */
@KtorDsl
public class RateLimitProviderConfig(internal val name: RateLimitName) {
    internal var requestKey: suspend (HttpRequestBuilder) -> Any = { Unit }
    internal var requestWeight: suspend (HttpRequestBuilder, key: Any) -> Int = { _, _ -> 1 }
    internal var rateLimiterFactory: ((key: Any) -> RateLimiter)? = null

    /**
     * Maximum time an attempt waits for this provider's tokens, including time behind other requests.
     * Defaults to five seconds. Each provider has its own waiting budget.
     * Zero or negative values attempt consumption once and reject immediately if tokens are unavailable.
     * Use [Duration.INFINITE] to wait until admission or cancellation.
     * Both immediate rejection and expiration fail with [RateLimitRejectedException].
     * A request-level `rateLimit(name) { maxWaitTime = ... }` overrides this value for the specified provider only.
     */
    public var maxWaitTime: Duration = 5.seconds

    /**
     * Creates a limiter for each distinct request key. Equal keys share one limiter and FIFO queue within this provider.
     * The factory is called once per key, and the limiter remains registered for the lifetime of the client.
     * Replaces any previously configured factory.
     */
    public fun rateLimiterFactory(factory: (key: Any) -> RateLimiter) {
        rateLimiterFactory = factory
    }

    /**
     * Configures a limiter with [limit] tokens, fully replenished every [refillPeriod], using the system clock.
     * Starts with a full bucket and uses [timeSource] for refill timing.
     */
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory { RateLimiter.default(limit, refillPeriod, timeSource) }
    }

    /**
     * Configures a limiter with [limit] tokens, fully replenished every [refillPeriod].
     * Refill timing uses [timeSource]; [clock] supplies epoch timestamps for limiter state.
     * The bucket starts full.
     */
    @OptIn(ExperimentalTime::class)
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        clock: Clock,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory {
            RateLimiter.default(limit, refillPeriod, clock, timeSource)
        }
    }

    /**
     * Configures an initially full limiter with [limit] tokens, replenishing one token every [refillInterval].
     * Uses the system clock for limiter timestamps and [timeSource] for refill timing.
     */
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory { RateLimiter.incrementalRefill(limit, refillInterval, timeSource) }
    }

    /**
     * Configures an initially full limiter with [limit] tokens, replenishing one token every [refillInterval].
     * Refill timing uses [timeSource]; [clock] supplies epoch timestamps for limiter state.
     */
    @OptIn(ExperimentalTime::class)
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        clock: Clock,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory {
            RateLimiter.incrementalRefill(limit, refillInterval, clock, timeSource)
        }
    }

    /**
     * Selects independent buckets within this provider. Keys must have stable equality and hash codes.
     * By default the key is [Unit], so all requests using the provider share one bucket.
     */
    public fun requestKey(block: suspend (HttpRequestBuilder) -> Any) {
        requestKey = block
    }

    /**
     * Selects the number of tokens consumed by an attempt. Defaults to one.
     * The key is the result of [requestKey] for the same request.
     * Zero consumes no tokens. The plugin throws [IllegalArgumentException] if the weight is negative.
     */
    public fun requestWeight(block: suspend (HttpRequestBuilder, key: Any) -> Int) {
        requestWeight = block
    }
}

internal class RateLimitProvider(config: RateLimitProviderConfig) {
    val name = config.name
    val requestKey = config.requestKey
    val requestWeight = config.requestWeight
    val maxWaitTime = config.maxWaitTime
    val rateLimiterFactory = checkNotNull(config.rateLimiterFactory) {
        "Please set rateLimiter, incrementalRefillRateLimiter or rateLimiterFactory for provider '${config.name.name}'"
    }
}
