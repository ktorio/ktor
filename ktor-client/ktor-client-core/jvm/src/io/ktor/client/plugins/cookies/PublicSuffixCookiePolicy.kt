/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.http.*

/**
 * A cookie policy that rejects explicit public-suffix domains according to [rules].
 *
 * Cookies without a domain attribute and cookies with IP address domains bypass public-suffix validation.
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

        val canonicalDomain = try {
            canonicalizeDomain(domain)
        } catch (_: IllegalArgumentException) {
            return false
        }

        return !rules.isPublicSuffix(canonicalDomain)
    }
}
