/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.client.plugins.cookies.publicsuffix.*

/**
 * Creates public-suffix rules from the list bundled in this module's JVM resources.
 *
 * The list is read and validated on each call, so reuse the returned rules instead of calling this repeatedly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.bundled)
 *
 * @throws IllegalStateException when the bundled list is missing or corrupted.
 */
public fun PublicSuffixRules.Companion.bundled(): PublicSuffixRules {
    val classLoader = PublicSuffixRules::class.java.classLoader
    val stream = classLoader.getResourceAsStream(PUBLIC_SUFFIX_RESOURCE)
        ?: error("Unable to load $PUBLIC_SUFFIX_RESOURCE")

    return stream.use { resource -> BinaryPublicSuffixMatcher.from(resource.readBytes()) }
}
