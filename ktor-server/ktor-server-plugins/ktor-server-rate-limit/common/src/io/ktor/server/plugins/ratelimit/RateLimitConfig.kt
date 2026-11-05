/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmInline
import kotlin.time.*

/**
 * A config for the [RateLimit] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitConfig)
 */
@KtorDsl
public class RateLimitConfig {

    internal val providers: MutableMap<RateLimitName, RateLimitProvider> = mutableMapOf()
    internal var global: RateLimitProvider? = null

    /**
     * Registers the Rate-Limit provider that can be used in sub-routes via the [Route.rateLimit] function.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitConfig.register)
     */
    public fun register(name: RateLimitName = LIMITER_NAME_EMPTY, block: RateLimitProviderConfig.() -> Unit) {
        if (providers.containsKey(name)) {
            throw IllegalStateException("Rate limit provider with name $name is already configured")
        }
        providers[name] = RateLimitProvider(
            RateLimitProviderConfig(name).apply {
                block()
            }
        )
    }

    /**
     * Registers the Rate-Limit provider that is applied to a whole application.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitConfig.global)
     */
    public fun global(block: RateLimitProviderConfig.() -> Unit) {
        global = RateLimitProvider(
            RateLimitProviderConfig(LIMITER_NAME_GLOBAL).apply {
                block()
            }
        )
    }
}

/**
 * A name of registered [RateLimit] provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitName)
 */
@JvmInline
public value class RateLimitName(internal val name: String)

/**
 * A config for [RateLimit] provider inside [RateLimiterConfig].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig)
 */
@KtorDsl
public class RateLimitProviderConfig(internal val name: RateLimitName) {

    internal var requestKey: suspend (ApplicationCall) -> Any = { }
    internal var requestWeight: suspend (ApplicationCall, key: Any) -> Int = { _, _ -> 1 }

    internal var modifyResponse: (ApplicationCall, RateLimiter.State) -> Unit = { call, state ->
        when (state) {
            is RateLimiter.State.Available -> {
                call.response.headers.appendIfAbsent("X-RateLimit-Limit", state.limit.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                call.response.headers.appendIfAbsent("X-RateLimit-Reset", formatHeaderSeconds(state.refillAtTimeMillis))
            }

            is RateLimiter.State.Exhausted -> {
                if (!call.response.headers.contains(HttpHeaders.RetryAfter)) {
                    call.response.header(HttpHeaders.RetryAfter, state.toWait.formatHeaderSeconds())
                }
            }
        }
    }

    @Deprecated("Use rateLimiterFactory. Retained for compatibility with call-dependent factories.")
    internal var rateLimiterProvider: ((call: ApplicationCall, key: Any) -> RateLimiter)? = null
    internal var rateLimiterFactory: ((key: Any) -> RateLimiter)? = null

    /**
     * Sets [RateLimit] for this provider based on [ApplicationCall] and `key` of this request.
     *
     * To migrate, include any identity needed to configure the limiter in [requestKey], and use
     * [rateLimiterFactory] to create the limiter from that key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.rateLimiter)
     */
    @Deprecated(
        "Use rateLimiterFactory. Include the identity needed to configure the limiter in the request key."
    )
    public fun rateLimiter(provider: (call: ApplicationCall, key: Any) -> RateLimiter) {
        @Suppress("DEPRECATION")
        rateLimiterProvider = provider
        rateLimiterFactory = null
    }

    /**
     * Sets a [factory] that creates a [RateLimiter] from the request key, independently of an [ApplicationCall].
     *
     * A limiter is shared by requests with equal keys using this provider, including requests to different routes.
     * The factory is called when no limiter exists for the provider and key. Created limiters remain registered
     * for the lifetime of the application. The key must contain all identity needed to configure the limiter.
     *
     * Calling this function replaces any previously configured limiter factory for this provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.rateLimiterFactory)
     *
     * @param factory creates a limiter for the key produced by [requestKey].
     */
    @Suppress("DEPRECATION")
    public fun rateLimiterFactory(factory: (key: Any) -> RateLimiter) {
        rateLimiterFactory = factory
        rateLimiterProvider = null
    }

    /**
     * Sets [RateLimit] for this provider
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.rateLimiter)
     */
    @Deprecated("Use the overload accepting kotlin.time.Clock.")
    @Suppress("DEPRECATION")
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        initialSize: Int = limit,
        clock: () -> Long
    ) {
        rateLimiterFactory {
            RateLimiter.default(limit = limit, refillPeriod = refillPeriod, initialSize = initialSize, clock = clock)
        }
    }

    /**
     * Configures a rate limiter with [limit] tokens that is fully refilled every [refillPeriod].
     *
     * Refill timing uses monotonic time. The [clock] supplies epoch timestamps for reset headers.
     * The [initialSize] parameter is ignored.
     */
    @OptIn(ExperimentalTime::class)
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        initialSize: Int = limit,
        clock: Clock = Clock.System
    ) {
        rateLimiterFactory {
            RateLimiter.default(limit = limit, refillPeriod = refillPeriod, initialSize = initialSize, clock = clock)
        }
    }

    @Deprecated("Use the overload accepting kotlin.time.Clock.")
    @Suppress("DEPRECATION")
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        clock: () -> Long
    ) {
        rateLimiterFactory {
            RateLimiter.incrementalRefill(limit = limit, refillInterval = refillInterval, clock = clock)
        }
    }

    /**
     * Configures a rate limiter with [limit] tokens that replenishes one token every [refillInterval].
     *
     * Refill timing uses monotonic time. The [clock] supplies epoch timestamps for reset headers.
     */
    @OptIn(ExperimentalTime::class)
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        clock: Clock = Clock.System
    ) {
        rateLimiterFactory {
            RateLimiter.incrementalRefill(limit = limit, refillInterval = refillInterval, clock = clock)
        }
    }

    /**
     * Sets a function that returns a key for a request. Requests with different keys will have independent Rate-Limits.
     * Keys should have good equals and hashCode implementations.
     * By default, the key is a [Unit], so all requests share the same Rate-Limit.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.requestKey)
     */
    public fun requestKey(block: suspend (ApplicationCall) -> Any) {
        requestKey = block
    }

    /**
     * Sets a function that returns a weight of a request.
     * Weight is used to calculate how many tokens are consumed by a request.
     * By default, weight is always 1.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.requestWeight)
     */
    public fun requestWeight(block: suspend (ApplicationCall, key: Any) -> Int) {
        requestWeight = block
    }

    /**
     * Sets a function that modifies response headers.
     * By default, these headers will be added:
     *
     * if request is allowed:
     *
     *    `X-RateLimit-Limit` limit setting
     *
     *    `X-RateLimit-Remaining` amount of tokens left
     *
     *    `X-RateLimit-Reset` UTC timestamp for next limit reset time in seconds
     *
     * if request is declined:
     *
     *    `Retry-After` time to wait for next limit reset time in seconds
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.modifyResponse)
     */
    public fun modifyResponse(block: (ApplicationCall, RateLimiter.State) -> Unit) {
        modifyResponse = block
    }
}

private fun Duration.formatHeaderSeconds(): String = formatHeaderSeconds(inWholeMilliseconds)

/**
 * Converts milliseconds to seconds using ceiling division.
 * This ensures that fractional seconds are always rounded up.
 */
private fun formatHeaderSeconds(milliseconds: Long): String =
    ((milliseconds + 999) / 1000).toString()
