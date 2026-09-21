/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import io.ktor.client.*
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.text.Normalizer
import java.text.Normalizer.Form.NFC

private const val PUBLIC_SUFFIX_LIST_URL = "https://publicsuffix.org/list/public_suffix_list.dat"

/**
 * Loads the Public Suffix List from [url] and creates a cookie policy that rejects explicit public-suffix domains.
 *
 * The list is loaded once, and both its ICANN and private sections are used. The returned policy does not refresh the
 * list automatically. This policy accepts cookies without a domain attribute and cookies with IP addresses.
 *
 * @param url the URL of a UTF-8 Public Suffix List.
 * @return a cookie acceptance policy backed by the loaded list.
 * @throws IllegalStateException if loading [url] returns an unsuccessful HTTP status.
 * @throws IllegalArgumentException if the returned list is empty or malformed.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.cookies.loadPublicSuffixCookiePolicy)
 */
public suspend fun HttpClient.loadPublicSuffixCookiePolicy(
    url: String = PUBLIC_SUFFIX_LIST_URL
): CookieAcceptancePolicy {
    val response = get(url) { expectSuccess = true }
    val rules = PublicSuffixRules.parse(response.bodyAsText())
    return PublicSuffixCookiePolicy(rules)
}

private class PublicSuffixCookiePolicy(
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
        fun parse(content: String): PublicSuffixRules {
            require(content.isNotBlank()) { "The Public Suffix List is empty" }

            val exact = mutableSetOf<String>()
            val wildcards = mutableSetOf<String>()
            val exceptions = mutableSetOf<String>()

            content.lineSequence().forEachIndexed { index, sourceLine ->
                val line = sourceLine.trim().removePrefix("\uFEFF")
                if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed

                val (target, rule) = when {
                    line.startsWith("!", ignoreCase = false) -> exceptions to line.drop(1)
                    line.startsWith("*.", ignoreCase = false) -> wildcards to line.drop(2)
                    else -> exact to line
                }

                require(rule.isNotEmpty() && '*' !in rule && '!' !in rule && rule.none { it.isWhitespace() }) {
                    "Malformed Public Suffix List rule at line ${index + 1}: $line"
                }

                try {
                    target += canonicalizeDomain(rule)
                } catch (cause: IllegalArgumentException) {
                    throw IllegalArgumentException(
                        "Malformed Public Suffix List rule at line ${index + 1}: $line",
                        cause
                    )
                }
            }

            require(exact.isNotEmpty() || wildcards.isNotEmpty() || exceptions.isNotEmpty()) {
                "The Public Suffix List contains no rules"
            }
            return PublicSuffixRules(exact, wildcards, exceptions)
        }
    }
}

private fun canonicalizeLabel(label: String): String {
    require(label.isNotEmpty()) { "Empty domain label" }

    val decoded = Punycode.decode(label) ?: throw IllegalArgumentException("Invalid domain label: $label")
    if (label.startsWith(Punycode.PREFIX, ignoreCase = true)) {
        require(decoded.any { it.code >= 0x80 }) { "Invalid Punycode label: $label" }
    }

    // NFC normalization closes equivalence gaps between PSL rules and cookie domains. Full UTS #46 mapping is
    // intentionally outside the scope of this policy.
    val normalized = Normalizer.normalize(decoded.lowercase(), NFC)
    val canonicalLabel = Punycode.encode(normalized)
        ?: throw IllegalArgumentException("Invalid domain label: $label")

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
