/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

// The canonical form is described in PublicSuffixRules.

private const val MAX_DOMAIN_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63

/**
 * Returns `true` if this string is a domain in the canonical form.
 */
internal fun String.isCanonicalDomain(): Boolean =
    length in 1..MAX_DOMAIN_LENGTH && split('.').all { it.isValidLabel() }

/**
 * Returns the canonical form of a Public Suffix List rule [domain], or `null` if it is not a valid domain name.
 *
 * @param normalize lowercases and normalizes text.
 */
internal fun canonicalizeDomainOrNull(domain: String, normalize: (String) -> String): String? {
    if (domain.length !in 1..MAX_DOMAIN_LENGTH) return null

    val normalizedDomain = Punycode.encode(normalize(domain))
    return normalizedDomain?.takeIf { it.isCanonicalDomain() }
}

private fun String.isValidLabel(): Boolean =
    length in 1..MAX_LABEL_LENGTH &&
        first() != '-' &&
        last() != '-' &&
        all { it in 'a'..'z' || it in '0'..'9' || it == '-' }
