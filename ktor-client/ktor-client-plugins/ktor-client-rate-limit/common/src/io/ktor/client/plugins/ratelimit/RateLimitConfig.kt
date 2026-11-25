/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.statement.*
import io.ktor.ratelimit.*
import io.ktor.utils.io.*
import kotlin.time.Duration

/**
 * Configuration for the client [RateLimit] plugin.
 * Providers are optional. By default, server cooldown headers delay subsequent requests to the same origin,
 * even when no providers are configured. Cooldown waiting has no plugin timeout and respects cancellation.
 */
@KtorDsl
public class RateLimitConfig {
    internal val providers: MutableMap<RateLimitName, RateLimitProvider> = mutableMapOf()
    internal var global: RateLimitProvider? = null
    internal var serverCooldownExtractor: suspend (HttpResponse) -> Duration? = { it.rateLimitDelay() }

    /**
     * Replaces the default server cooldown extractor with [extract].
     * Positive durations postpone admission for the response's origin. Zero, negative or `null` values add no cooldown.
     * Existing cooldowns are never shortened or cleared by a response.
     * Use [HttpResponse.rateLimitDelay] inside [extract] to fall back explicitly to built-in header interpretation.
     * The extractor runs before the response is delivered; it should not consume the response body.
     * To ignore server cooldown feedback, configure `serverCooldown { null }`.
     */
    public fun serverCooldown(extract: suspend (HttpResponse) -> Duration?) {
        serverCooldownExtractor = extract
    }

    /**
     * Registers a provider selected explicitly with the request's `rateLimit(name)` function.
     * Omitting [name] registers the nameless provider, which requests select with `rateLimit()`.
     * Registration alone does not apply the provider to requests.
     *
     * @throws IllegalStateException if [name] is already registered or no limiter factory is configured.
     */
    @OptIn(InternalAPI::class)
    public fun register(name: RateLimitName = LIMITER_NAME_EMPTY, block: RateLimitProviderConfig.() -> Unit) {
        require(name != LIMITER_NAME_GLOBAL) { "Use global { ... } to configure the global rate limit provider" }
        check(name !in providers) { "Rate limit provider with name $name is already configured" }
        providers[name] = RateLimitProvider(RateLimitProviderConfig(name).apply(block))
    }

    /**
     * Registers a provider applied to every attempt, in addition to all explicitly selected providers.
     * Replaces any previously configured global provider.
     */
    @OptIn(InternalAPI::class)
    public fun global(block: RateLimitProviderConfig.() -> Unit) {
        global = RateLimitProvider(RateLimitProviderConfig(LIMITER_NAME_GLOBAL).apply(block))
    }
}
