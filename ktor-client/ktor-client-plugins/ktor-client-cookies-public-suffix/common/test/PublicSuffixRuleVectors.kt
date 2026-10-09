/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies.publicsuffix

import io.ktor.client.plugins.cookies.*
import kotlin.test.*

/** Rule vectors that every bundled-rules implementation must agree on. */
internal val bundledPublicSuffixVectors: Map<String, Boolean> = mapOf(
    "com" to true,
    "co.uk" to true,
    "github.io" to true,
    "foo.ck" to true,
    "xn--55qx5d.cn" to true,
    "www.ck" to false,
    "example.com" to false,
    "example.co.uk" to false,
    "user.github.io" to false,
)

internal fun assertBundledPublicSuffixVectors(rules: PublicSuffixRules) {
    for ((domain, expected) in bundledPublicSuffixVectors) {
        assertEquals(expected, rules.isPublicSuffix(domain), domain)
    }
}
