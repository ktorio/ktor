/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import android.content.Context
import io.ktor.client.plugins.cookies.publicsuffix.*

/**
 * Creates lazily loaded public-suffix rules from the list bundled in this module's Android assets.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.bundled)
 *
 * @param context a context used to access this module's packaged assets.
 */
public fun PublicSuffixRules.Companion.bundled(context: Context): PublicSuffixRules =
    AndroidBundledPublicSuffixRules(context.applicationContext)

private class AndroidBundledPublicSuffixRules(
    private val context: Context
) : PublicSuffixRules {
    private val matcher: BinaryPublicSuffixMatcher by lazy {
        context.assets.open(PUBLIC_SUFFIX_RESOURCE).use { resource ->
            BinaryPublicSuffixMatcher.from(resource.readBytes())
        }
    }

    override fun isPublicSuffix(canonicalDomain: String): Boolean = matcher.isPublicSuffix(canonicalDomain)
}
