/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.client.plugins.cookies.publicsuffix.*

/**
 * Creates public-suffix rules backed by the list bundled in this module's JVM resources.
 *
 * Creating the rules is cheap: the list is read and validated on the first [PublicSuffixRules.isPublicSuffix] call,
 * which blocks the calling thread while it runs. If loading fails, that call throws and the next call retries.
 * Use [loadBundled] to load the list immediately instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.bundled)
 */
public fun PublicSuffixRules.Companion.bundled(): PublicSuffixRules = LazyPublicSuffixRules(::readBundledResource)

/**
 * Loads public-suffix rules from the list bundled in this module's JVM resources.
 *
 * The list is read and validated before this function returns, so a missing or corrupted list fails here rather than
 * on first use. Reuse the returned rules instead of calling this repeatedly. Use [bundled] to defer loading.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.loadBundled)
 *
 * @throws IllegalStateException when the bundled list is missing or corrupted.
 */
public fun PublicSuffixRules.Companion.loadBundled(): PublicSuffixRules =
    BinaryPublicSuffixMatcher.from(readBundledResource())

private fun readBundledResource(): ByteArray {
    val stream = BinaryPublicSuffixMatcher::class.java.getResourceAsStream("/$PUBLIC_SUFFIX_RESOURCE")
        ?: error("Unable to load $PUBLIC_SUFFIX_RESOURCE")

    return stream.use { it.readBytes() }
}
