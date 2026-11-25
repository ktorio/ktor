/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.ratelimit

import io.ktor.utils.io.*
import kotlin.jvm.JvmInline

/**
 * Identifies a registered rate limit provider.
 *
 * @property name the provider name.
 */
@JvmInline
public value class RateLimitName(public val name: String) {
    public companion object {
        /** The name of the global rate limit provider. */
        @OptIn(InternalAPI::class)
        public val global: RateLimitName
            get() = LIMITER_NAME_GLOBAL
    }
}

/** The reserved name for the global rate limit provider. */
@InternalAPI
public val LIMITER_NAME_GLOBAL: RateLimitName = RateLimitName("KTOR_GLOBAL_RATE_LIMITER")

/** The reserved name for a rate limit provider registered without a name. */
@InternalAPI
public val LIMITER_NAME_EMPTY: RateLimitName = RateLimitName("KTOR_NO_NAME_RATE_LIMITER")
