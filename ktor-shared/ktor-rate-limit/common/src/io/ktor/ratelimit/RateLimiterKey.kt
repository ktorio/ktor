/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

/**
 * Identifies a rate limiter by its provider name and request key.
 *
 * @property providerName the name of the provider that owns the limiter.
 * @property key the request key that selects the limiter within the provider.
 */
public class RateLimiterKey(
    public val providerName: RateLimitName,
    public val key: Any,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RateLimiterKey) return false
        return providerName == other.providerName && key == other.key
    }

    override fun hashCode(): Int = 31 * providerName.hashCode() + key.hashCode()
}
