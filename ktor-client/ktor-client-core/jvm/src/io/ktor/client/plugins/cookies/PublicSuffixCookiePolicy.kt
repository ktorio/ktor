/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import io.ktor.util.*

/**
 * A cookie policy that rejects explicit public-suffix domains according to [rules].
 *
 * Cookies without a domain attribute and cookies with IP address domains bypass public-suffix validation.
 * Otherwise, the domain attribute is stripped of a leading dot and lowercased, and must then be in the canonical form
 * described by [PublicSuffixRules]. A cookie whose domain is not, for example because it contains non-ASCII
 * characters, is rejected without consulting [rules].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.PublicSuffixCookiePolicy)
 *
 * @param rules the public-suffix rules to apply.
 */
public class PublicSuffixCookiePolicy(
    private val rules: PublicSuffixRules
) : CookieAcceptancePolicy {

    override suspend fun shouldAccept(requestUrl: Url, cookie: Cookie): Boolean {
        val domain = cookie.domain?.takeUnless { it.isBlank() }?.trimStart('.') ?: return true
        if (hostIsIp(domain)) return true

        val canonicalDomain = domain.toLowerCasePreservingASCIIRules()
        return canonicalDomain.isCanonicalDomain() && !rules.isPublicSuffix(canonicalDomain)
    }
}
