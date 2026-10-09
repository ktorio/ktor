/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

import java.text.Normalizer
import java.text.Normalizer.Form.NFKC

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
        when (val line = sourceLine.trim().removePrefix("\uFEFF")) {
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

                val isException = line.startsWith('!')
                val wildcard = if (line.startsWith("*.")) "*." else ""
                val domain = line.removePrefix("!").removePrefix(wildcard)
                val canonicalDomain = canonicalizeDomainOrNull(domain, String::toNfkcLowercase)
                    ?: malformedList(source, index, line)

                if (isException) {
                    exceptions += canonicalDomain
                } else {
                    rules += wildcard + canonicalDomain
                }
            }
        }
    }

    require(section == null && icannFound && privateFound && rules.isNotEmpty()) {
        "The Public Suffix List from $source has missing or incomplete sections"
    }

    return PublicSuffixListRules(rules, exceptions)
}

private fun malformedList(source: String, index: Int, line: String): Nothing {
    throw IllegalArgumentException("Malformed Public Suffix List from $source at line ${index + 1}: $line")
}

// The normalization used for public-suffix checks, which runs on the JVM only.
// Lowercasing can produce characters that are not NFKC-normalized, so normalize again afterward.
internal fun String.toNfkcLowercase(): String =
    Normalizer.normalize(Normalizer.normalize(this, NFKC).lowercase(), NFKC)
