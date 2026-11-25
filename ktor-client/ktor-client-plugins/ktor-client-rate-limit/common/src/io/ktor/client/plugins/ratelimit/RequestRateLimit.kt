/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.ratelimit

import io.ktor.client.request.*
import io.ktor.ratelimit.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.time.Duration

internal val RateLimitProviderNamesKey = AttributeKey<List<RateLimitName>>("RateLimitProviders")
internal val RateLimitMaxWaitTimesKey = AttributeKey<Map<RateLimitName, Duration>>("RateLimitMaxWaitTimes")

/**
 * Per-request overrides for the provider selected by a [rateLimit] call.
 */
@KtorDsl
public class RateLimitRequestConfig {
    /**
     * Overrides the specified provider's maximum wait time for this request only.
     * Includes FIFO and token waiting. Zero or negative values reject immediately if admission
     * is unavailable. Set to `null` to use the selected provider's configured value.
     * The override is preserved for retries and redirects.
     */
    public var maxWaitTime: Duration? = null
}

/**
 * Adds a registered provider to this request and its retries and redirects.
 * Omitting [name] selects the nameless provider. Repeated calls accumulate providers in selection order.
 * Each provider is applied once; selecting it again preserves its original position.
 * The global provider applies before all selected providers.
 * Use [RateLimitName.global] to configure the global provider for this request.
 * Tokens consumed from earlier providers are not returned if admission to a later provider fails.
 * [configure] updates overrides only for [name], or the nameless provider when [name] is omitted.
 * Settings from previous calls for the same provider are preserved unless explicitly replaced or reset to `null`.
 * Other providers retain their own settings.
 */
@OptIn(InternalAPI::class)
public fun HttpRequestBuilder.rateLimit(
    name: RateLimitName = LIMITER_NAME_EMPTY,
    configure: RateLimitRequestConfig.() -> Unit = {}
) {
    val overrides = attributes.getOrNull(RateLimitMaxWaitTimesKey).orEmpty()
    val config = RateLimitRequestConfig().apply {
        maxWaitTime = overrides[name]
    }.apply(configure)
    if (name != RateLimitName.global) {
        val existing = attributes.getOrNull(RateLimitProviderNamesKey).orEmpty()
        attributes.put(RateLimitProviderNamesKey, (existing + name).distinct())
    }
    val maxWaitTime = config.maxWaitTime
    val updated = if (maxWaitTime == null) overrides - name else overrides + (name to maxWaitTime)
    if (updated.isEmpty()) {
        attributes.remove(RateLimitMaxWaitTimesKey)
    } else {
        attributes.put(RateLimitMaxWaitTimesKey, updated)
    }
}
