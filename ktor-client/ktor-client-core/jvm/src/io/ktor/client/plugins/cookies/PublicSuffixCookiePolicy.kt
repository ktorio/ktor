/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.http.*
import java.text.Normalizer
import java.text.Normalizer.Form.NFC

/**
 * A cookie policy that rejects explicit public-suffix domains using the bundled Public Suffix List.
 *
 * Both the ICANN and private sections are used. This policy accepts cookies without a domain attribute and cookies
 * with IP addresses. Update the bundled list with `:ktor-client-core:updatePublicSuffixList`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.PublicSuffixCookiePolicy)
 */
public object PublicSuffixCookiePolicy : CookieAcceptancePolicy {
    private val rules: PublicSuffixRules by lazy(PublicSuffixRules::bundled)

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

private class PublicSuffixRules(
    private val exact: Set<String>,
    private val wildcards: Set<String>,
    private val exceptions: Set<String>
) {
    fun isPublicSuffix(domain: String): Boolean {
        val labels = domain.split('.')
        var matchingRuleLabels = 1

        for (index in labels.indices) {
            val suffix = labels.subList(index, labels.size).joinToString(".")
            if (suffix in exceptions) return false
            if (suffix in exact) matchingRuleLabels = maxOf(matchingRuleLabels, labels.size - index)
            if (index > 0 && suffix in wildcards) {
                matchingRuleLabels = maxOf(matchingRuleLabels, labels.size - index + 1)
            }
        }

        return labels.size == matchingRuleLabels
    }

    companion object {
        fun bundled(): PublicSuffixRules = PublicSuffixRules(
            exact = canonicalize(PUBLIC_SUFFIX_EXACT_RULE_CHUNKS.asRules()),
            wildcards = canonicalize(PUBLIC_SUFFIX_WILDCARD_RULE_CHUNKS.asRules()),
            exceptions = canonicalize(PUBLIC_SUFFIX_EXCEPTION_RULE_CHUNKS.asRules())
        )

        private fun canonicalize(rules: Sequence<String>): Set<String> =
            rules.map(::canonicalizeDomain).toSet()
    }
}

private fun Array<String>.asRules(): Sequence<String> = asSequence().flatMap { it.lineSequence() }

private fun canonicalizeLabel(label: String): String {
    require(label.isNotEmpty()) { "Empty domain label" }

    val decoded = requireNotNull(Punycode.decode(label)) { "Invalid domain label: $label" }
    if (label.startsWith(Punycode.PREFIX, ignoreCase = true)) {
        require(decoded.any { it.code >= 0x80 }) { "Invalid Punycode label: $label" }
    }

    val normalized = Normalizer.normalize(decoded.lowercase(), NFC)
    val canonicalLabel = requireNotNull(Punycode.encode(normalized)) {
        "Invalid domain label: $label"
    }

    require(canonicalLabel.length <= 63) {
        "Invalid domain label: $label"
    }
    require(canonicalLabel.first() != '-' && canonicalLabel.last() != '-') {
        "Invalid domain label: $label"
    }
    require(canonicalLabel.all { it in 'a'..'z' || it in '0'..'9' || it == '-' }) {
        "Invalid domain label: $label"
    }

    return canonicalLabel
}

private fun canonicalizeDomain(domain: String): String {
    require(domain.isNotEmpty() && domain.length <= 253) { "Invalid domain: $domain" }

    val result = domain
        .split('.')
        .joinToString(separator = ".", transform = ::canonicalizeLabel)

    require(result.length <= 253) { "Invalid domain: $domain" }
    return result
}
