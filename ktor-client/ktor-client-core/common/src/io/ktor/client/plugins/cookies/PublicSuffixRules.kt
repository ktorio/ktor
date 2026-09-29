/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

/**
 * Determines whether a canonical domain is a public suffix.
 *
 * A canonical domain consists of lowercase ASCII labels separated by single dots, with no leading or trailing dot,
 * and has at most 253 characters. Each label has 1 to 63 characters from `a-z`, `0-9`, and `-`, and does not start
 * or end with `-`. An internationalized label is represented by its Punycode A-label, for example,
 * `xn--mnchen-3ya` for `münchen`. IP addresses are never passed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.PublicSuffixRules)
 */
public fun interface PublicSuffixRules {
    /**
     * @return `true` when [canonicalDomain] is a public suffix.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.PublicSuffixRules.isPublicSuffix)
     */
    public fun isPublicSuffix(canonicalDomain: String): Boolean

    public companion object
}
