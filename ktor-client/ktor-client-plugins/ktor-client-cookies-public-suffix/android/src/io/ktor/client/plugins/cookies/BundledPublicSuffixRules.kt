/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import android.content.Context
import io.ktor.client.plugins.cookies.publicsuffix.*

/**
 * Creates public-suffix rules backed by the list bundled in this module's Android assets.
 *
 * Creating the rules is cheap: the list is read and validated on the first [PublicSuffixRules.isPublicSuffix] call,
 * which blocks the calling thread while it runs. If loading fails, that call throws and the next call retries.
 * Use [loadBundled] to load the list immediately instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.bundled)
 *
 * @param context a context used to access this module's packaged assets. Only its application context is retained.
 */
public fun PublicSuffixRules.Companion.bundled(context: Context): PublicSuffixRules {
    val applicationContext = context.applicationContext
    return LazyPublicSuffixRules { applicationContext.readBundledAsset() }
}

/**
 * Loads public-suffix rules from the list bundled in this module's Android assets.
 *
 * The list is read and validated before this function returns, so a missing or corrupted list fails here rather than
 * on first use. Reuse the returned rules instead of calling this repeatedly. Use [bundled] to defer loading.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.loadBundled)
 *
 * @param context a context used to access this module's packaged assets.
 * @throws java.io.IOException when the bundled list cannot be read.
 * @throws IllegalStateException when the bundled list is corrupted.
 */
public fun PublicSuffixRules.Companion.loadBundled(context: Context): PublicSuffixRules {
    val resource = context.applicationContext.readBundledAsset()
    return BinaryPublicSuffixMatcher.from(resource)
}

private fun Context.readBundledAsset(): ByteArray = assets.open(PUBLIC_SUFFIX_RESOURCE).use { it.readBytes() }
