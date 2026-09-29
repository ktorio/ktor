/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import java.text.Normalizer
import java.text.Normalizer.Form.NFKC

// This file, Punycode.kt, and IdnaDomains.kt are also compiled into build-logic for the `updatePublicSuffixList` task,
// so the bundled list and the runtime canonicalize domains identically.
// Keep them free of dependencies other than the Kotlin standard library and the JDK.

private const val BEGIN_ICANN_DOMAINS = "// ===BEGIN ICANN DOMAINS==="
private const val END_ICANN_DOMAINS = "// ===END ICANN DOMAINS==="
private const val BEGIN_PRIVATE_DOMAINS = "// ===BEGIN PRIVATE DOMAINS==="
private const val END_PRIVATE_DOMAINS = "// ===END PRIVATE DOMAINS==="

private enum class PublicSuffixSection {
    ICANN,
    PRIVATE
}

/**
 * Canonical Public Suffix List rules. Wildcard rules keep their `*.` prefix; exception rules drop their `!` prefix.
 */
internal class PublicSuffixListRules(
    val rules: Set<String>,
    val exceptions: Set<String>
)

/**
 * Parses the Public Suffix List [content] loaded from [source].
 *
 * @throws IllegalArgumentException when the list is empty, malformed, or misses the ICANN or private section.
 */
internal fun parsePublicSuffixList(content: String, source: String): PublicSuffixListRules {
    require(content.isNotBlank()) {
        "The Public Suffix List from $source is empty"
    }

    val rules = mutableSetOf<String>()
    val exceptions = mutableSetOf<String>()
    var section: PublicSuffixSection? = null
    var icannFound = false
    var privateFound = false

    content.lineSequence().forEachIndexed { index, sourceLine ->
        when (val line = sourceLine.trim().removePrefix("﻿")) {
            BEGIN_ICANN_DOMAINS -> {
                if (section != null || icannFound) malformedList(source, index, line)
                section = PublicSuffixSection.ICANN
                icannFound = true
            }

            END_ICANN_DOMAINS -> {
                if (section != PublicSuffixSection.ICANN) malformedList(source, index, line)
                section = null
            }

            BEGIN_PRIVATE_DOMAINS -> {
                if (section != null || privateFound) malformedList(source, index, line)
                section = PublicSuffixSection.PRIVATE
                privateFound = true
            }

            END_PRIVATE_DOMAINS -> {
                if (section != PublicSuffixSection.PRIVATE) malformedList(source, index, line)
                section = null
            }

            else -> {
                if (line.isEmpty() || line.startsWith("//")) return@forEachIndexed
                if (section == null) malformedList(source, index, line)
                try {
                    when {
                        line.startsWith('!') -> exceptions += canonicalizeRule(line.drop(1))
                        line.startsWith("*.") -> rules += "*.${canonicalizeRule(line.drop(2))}"
                        else -> rules += canonicalizeRule(line)
                    }
                } catch (cause: IllegalArgumentException) {
                    malformedList(source, index, line, cause)
                }
            }
        }
    }

    require(section == null && icannFound && privateFound && rules.isNotEmpty()) {
        "The Public Suffix List from $source has missing or incomplete sections"
    }

    return PublicSuffixListRules(rules, exceptions)
}

private fun canonicalizeRule(rule: String): String {
    require(rule.isNotEmpty() && '*' !in rule && '!' !in rule) { "Invalid rule: $rule" }
    require(rule.none { it.isWhitespace() || it.isISOControl() }) { "Invalid rule: $rule" }
    return canonicalizeDomain(rule)
}

private fun malformedList(source: String, index: Int, line: String, cause: Throwable? = null): Nothing {
    throw IllegalArgumentException("Malformed Public Suffix List from $source at line ${index + 1}: $line", cause)
}

/**
 * Converts [domain] to its canonical form: NFKC-normalized, lowercase, and Punycode-encoded,
 * with the full stop variants that UTS #46 maps to `.` treated as label separators.
 *
 * @throws IllegalArgumentException when [domain] is not a valid domain name.
 */
internal fun canonicalizeDomain(domain: String): String {
    require(domain.isNotEmpty() && domain.length <= 253) { "Invalid domain: $domain" }

    val result = domain.toNfkcLowercase()
        .replaceIdnaSeparators()
        .split('.')
        .joinToString(separator = ".", transform = ::canonicalizeLabel)

    require(result.length <= 253) { "Invalid domain: $domain" }
    return result
}

private fun canonicalizeLabel(label: String): String {
    require(label.isNotEmpty()) { "Empty domain label" }

    val decoded = requireNotNull(Punycode.decode(label)) { "Invalid domain label: $label" }
    if (label.startsWith(Punycode.PREFIX, ignoreCase = true)) {
        require(decoded.any { it.code >= 0x80 }) { "Invalid Punycode label: $label" }
    }

    // Punycode labels are decoded only here, so their content is mapped here as well.
    // A label must not smuggle a separator: for example, U+2488 normalizes to "1.".
    val normalized = decoded.toNfkcLowercase()
    require(normalized.none { it == '.' || it in IDNA_SEPARATORS }) { "Invalid domain label: $label" }

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

// Lowercasing can produce characters that are not NFKC-normalized, so normalize again afterward.
private fun String.toNfkcLowercase(): String =
    Normalizer.normalize(Normalizer.normalize(this, NFKC).lowercase(), NFKC)
