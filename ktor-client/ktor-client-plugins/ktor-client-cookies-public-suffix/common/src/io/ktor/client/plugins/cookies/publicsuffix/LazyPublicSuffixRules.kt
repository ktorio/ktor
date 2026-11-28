/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies.publicsuffix

import io.ktor.client.plugins.cookies.PublicSuffixRules

/**
 * Rules that read and validate the list returned by [load] on first use.
 * A failed load is not cached, so it is retried on the next use.
 */
internal class LazyPublicSuffixRules(load: () -> ByteArray) : PublicSuffixRules {
    private val matcher: BinaryPublicSuffixMatcher by lazy { BinaryPublicSuffixMatcher.from(load()) }

    override fun isPublicSuffix(canonicalDomain: String): Boolean = matcher.isPublicSuffix(canonicalDomain)
}
