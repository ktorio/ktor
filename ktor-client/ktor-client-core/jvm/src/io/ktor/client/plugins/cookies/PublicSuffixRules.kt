/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

private const val DEFAULT_PUBLIC_SUFFIX_LIST_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

/**
 * Loads public-suffix rules from [url].
 *
 * The response must contain a successful, nonempty Public Suffix List with both the ICANN and private sections.
 * Loading and parsing failures are propagated to the caller.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.loadPublicSuffixRules)
 *
 * @param url the Public Suffix List URL.
 * @throws kotlinx.io.IOException when the response is unsuccessful.
 * @throws IllegalArgumentException when the list is malformed.
 */
public suspend fun HttpClient.loadPublicSuffixRules(
    url: String = DEFAULT_PUBLIC_SUFFIX_LIST_URL
): PublicSuffixRules {
    val response = get(url) { expectSuccess = true }
    val rules = parsePublicSuffixList(content = response.bodyAsText(), source = url)
    return SetPublicSuffixRules(rules.rules, rules.exceptions)
}

private class SetPublicSuffixRules(
    private val rules: Set<String>,
    private val exceptions: Set<String>
) : PublicSuffixRules {
    override fun isPublicSuffix(canonicalDomain: String): Boolean {
        if (canonicalDomain in exceptions) return false
        if (canonicalDomain in rules) return true

        val firstDot = canonicalDomain.indexOf('.')
        if (firstDot < 0) return true

        val prefix = "*.${canonicalDomain.substring(firstDot + 1)}"
        return prefix in rules
    }
}
