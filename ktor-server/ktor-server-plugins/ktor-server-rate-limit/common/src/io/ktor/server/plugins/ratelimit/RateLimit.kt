/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.ratelimit

import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.collections.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmName

internal val LOGGER = KtorSimpleLogger("io.ktor.server.plugins.ratelimit.RateLimit")
internal val LIMITER_NAME_GLOBAL = RateLimitName("KTOR_GLOBAL_RATE_LIMITER")
internal val LIMITER_NAME_EMPTY = RateLimitName("KTOR_NO_NAME_RATE_LIMITER")

internal data class ProviderKey(
    private val name: RateLimitName,
    private val key: Any,
)

internal val RateLimiterInstancesRegistryKey =
    AttributeKey<ConcurrentMap<ProviderKey, RateLimiter>>("RateLimiterInstancesRegistryKey")

internal val RateLimiterConfigsRegistryKey =
    AttributeKey<Map<RateLimitName, RateLimitProvider>>("RateLimiterConfigsRegistryKey")

@OptIn(InternalAPI::class)
@PublicAPICandidate("3.0.0")
// Make it public in 3.0.0 and change Pair to a separate class
internal val RateLimitersForCallKey =
    AttributeKey<List<Pair<RateLimitName, RateLimiter>>>("RateLimitersForCallKey")

/**
 * A plugin that provides rate limiting for incoming requests.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.RateLimit)
 */
public val RateLimit: ApplicationPlugin<RateLimitConfig> = createApplicationPlugin("RateLimit", ::RateLimitConfig) {
    val global = pluginConfig.global
    val providers = when {
        global != null -> pluginConfig.providers + (LIMITER_NAME_GLOBAL to global)
        else -> pluginConfig.providers.toMap()
    }
    check(providers.isNotEmpty()) { "At least one provider must be specified" }
    application.attributes.put(RateLimiterConfigsRegistryKey, providers)

    if (global == null) return@createApplicationPlugin
    application.install(RateLimitApplicationInterceptors) {
        this.providerNames = listOf(LIMITER_NAME_GLOBAL)
    }
}

/**
 * Returns the [RateLimiter] associated with [providerName] and [key], creating it if necessary.
 *
 * The limiter is shared with requests that use the same provider and an equal request key.
 * Creating a limiter uses [RateLimitProviderConfig.rateLimiterFactory] and does not consume tokens.
 * An existing limiter can also be returned for providers configured with the deprecated call-dependent factory.
 *
 * Resolve the limiter again when querying its state, as fully refilled limiters can be evicted and replaced.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.ratelimit.getRateLimiter)
 *
 * @param providerName the name of the registered rate limit provider.
 * @param key the same key that the provider's [RateLimitProviderConfig.requestKey] returns for limited requests.
 * @return the limiter registered for this provider and key.
 * @throws IllegalArgumentException if [providerName] is not registered.
 * @throws IllegalStateException if [RateLimit] is not installed, or creating a missing limiter requires a call.
 */
@JvmName("getRateLimiter")
public fun Application.getRateLimiter(providerName: RateLimitName, key: Any): RateLimiter {
    val providers = checkNotNull(attributes.getOrNull(RateLimiterConfigsRegistryKey)) {
        "Install RateLimit before resolving a rate limiter"
    }
    val provider = requireNotNull(providers[providerName]) {
        "Rate limit provider '$providerName' is not registered"
    }
    val registry = attributes.computeIfAbsent(RateLimiterInstancesRegistryKey) { ConcurrentMap() }

    return registry.computeIfAbsent(ProviderKey(providerName, key)) {
        val factory = checkNotNull(provider.rateLimiterFactory) {
            "Provider '$providerName' uses a call-dependent factory. " +
                "Configure rateLimiterFactory to create limiters without a call."
        }
        factory(key)
    }
}

internal class RateLimitProvider(config: RateLimitProviderConfig) {
    val name = config.name
    val requestKey = config.requestKey
    val requestWeight = config.requestWeight
    val modifyResponse = config.modifyResponse
    val rateLimiterFactory = config.rateLimiterFactory

    @Suppress("DEPRECATION")
    val rateLimiter = config.rateLimiterProvider

    init {
        check(rateLimiterFactory != null || rateLimiter != null) {
            "Please set rateLimiter or rateLimiterFactory in the plugin install block"
        }
    }
}
