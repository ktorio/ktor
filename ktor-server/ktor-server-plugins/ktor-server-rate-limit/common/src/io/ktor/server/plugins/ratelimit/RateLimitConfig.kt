/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.http.*
import io.ktor.ratelimit.LIMITER_NAME_EMPTY
import io.ktor.ratelimit.LIMITER_NAME_GLOBAL
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.date.*
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
     * Registers the unnamed rate limit provider.
     *
     * @param block configures the provider.
     * @throws IllegalStateException if the unnamed provider is already registered.
     */
    @OptIn(InternalAPI::class)
    public fun register(block: RateLimitProviderConfig.() -> Unit) {
        register(io.ktor.ratelimit.LIMITER_NAME_EMPTY, block)
    }

    /**
     * Registers a rate limit provider under a shared name.
     *
     * @param name the name used to select the provider.
     * @param block configures the provider.
     * @throws IllegalStateException if a provider with [name] is already registered.
     */
    @Suppress("DEPRECATION")
    public fun register(name: io.ktor.ratelimit.RateLimitName, block: RateLimitProviderConfig.() -> Unit) {
        register(RateLimitName(name.name), block)
    }

    /**
     * Registers the Rate-Limit provider that can be used in sub-routes via the [Route.rateLimit] function.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitConfig.register)
     */
    @Deprecated("Use the overload accepting io.ktor.ratelimit.RateLimitName.")
    @OptIn(InternalAPI::class)
    public fun register(
        name: RateLimitName = RateLimitName(LIMITER_NAME_EMPTY.name),
        block: RateLimitProviderConfig.() -> Unit,
    ) {
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
    @OptIn(InternalAPI::class)
    @Suppress("DEPRECATION")
    public fun global(block: RateLimitProviderConfig.() -> Unit) {
        global = RateLimitProvider(
            RateLimitProviderConfig(RateLimitName(LIMITER_NAME_GLOBAL.name)).apply {
                block()
            }
        )
    }
}

/**
 * A name of registered [RateLimit] provider.
 *
 * @property name the provider identifier supplied at construction.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitName)
 */
@Deprecated("Use io.ktor.ratelimit.RateLimitName.")
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
     * Sets a [factory] that creates a shared [io.ktor.ratelimit.RateLimiter] from the request key,
     * independently of an [ApplicationCall].
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
    public fun rateLimiterFactory(factory: (key: Any) -> io.ktor.ratelimit.RateLimiter) {
        rateLimiterFactory = { key -> factory(key).asServerRateLimiter() }
        rateLimiterProvider = null
    }

    /**
     * Sets [RateLimit] for this provider
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimitProviderConfig.rateLimiter)
     */
    @OptIn(InternalAPI::class)
    @Deprecated("Use the overload accepting kotlin.time.Clock.")
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        @Suppress("UNUSED_PARAMETER") initialSize: Int = limit,
        clock: () -> Long = { getTimeMillis() }
    ) {
        rateLimiterFactory {
            io.ktor.ratelimit.RateLimiter.default(limit, refillPeriod, clock)
        }
    }

    /**
     * Configures a rate limiter with [limit] tokens that is fully refilled every [refillPeriod], using [Clock.System].
     * Starts with a full bucket and uses [timeSource] for refill timing.
     */
    @OptIn(ExperimentalTime::class)
    public fun rateLimiter(
        limit: Int,
        refillPeriod: Duration,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiter(limit, refillPeriod, Clock.System, timeSource)
    }

    /**
     * Configures a rate limiter with [limit] tokens that is fully refilled every [refillPeriod].
     *
     * Refill timing uses [timeSource]. The [clock] supplies epoch timestamps for reset headers.
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
            io.ktor.ratelimit.RateLimiter.default(limit, refillPeriod, clock, timeSource)
        }
    }

    /**
     * Configures an initially full limiter with [limit] tokens, replenishing one token every [refillInterval].
     * Uses the system clock for reset timestamps and [timeSource] for refill timing.
     */
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory { io.ktor.ratelimit.RateLimiter.incrementalRefill(limit, refillInterval, timeSource) }
    }

    /**
     * Configures a rate limiter with [limit] tokens that replenishes one token every [refillInterval].
     *
     * Refill timing uses [timeSource]. The [clock] supplies epoch timestamps for reset headers.
     */
    @OptIn(ExperimentalTime::class)
    public fun incrementalRefillRateLimiter(
        limit: Int,
        refillInterval: Duration,
        clock: Clock,
        timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic
    ) {
        rateLimiterFactory {
            io.ktor.ratelimit.RateLimiter.incrementalRefill(limit, refillInterval, clock, timeSource)
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
     * Zero consumes no tokens. The plugin throws [IllegalArgumentException] if the weight is negative.
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
    @Deprecated("Use modifySharedResponse with io.ktor.ratelimit.RateLimiter.State.")
    public fun modifyResponse(block: (ApplicationCall, RateLimiter.State) -> Unit) {
        modifyResponse = block
    }

    /**
     * Sets a [block] that modifies the response using the shared limiter state.
     *
     * Replaces the default rate limit headers and any previously configured response callback.
     *
     * @param block receives the call and the result of consuming its request weight.
     */
    public fun modifySharedResponse(block: (ApplicationCall, io.ktor.ratelimit.RateLimiter.State) -> Unit) {
        modifyResponse = { call, state -> block(call, state.asSharedState()) }
    }
}

private fun Duration.formatHeaderSeconds(): String = formatHeaderSeconds(inWholeMilliseconds)

/**
 * Converts milliseconds to seconds using ceiling division.
 * This ensures that fractional seconds are always rounded up.
 */
private fun formatHeaderSeconds(milliseconds: Long): String =
    ((milliseconds + 999) / 1000).toString()
