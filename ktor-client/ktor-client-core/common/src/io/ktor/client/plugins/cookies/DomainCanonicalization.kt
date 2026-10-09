/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

// The canonical form is described in PublicSuffixRules.

private const val MAX_DOMAIN_LENGTH = 253
private const val MAX_LABEL_LENGTH = 63

private val INVISIBLE_CATEGORIES = setOf(CharCategory.CONTROL, CharCategory.FORMAT)

// Full stops that UTS #46 maps to '.': ideographic, fullwidth, and halfwidth ideographic
private val FULL_STOP_VARIANTS = charArrayOf('\u3002', '\uFF0E', '\uFF61')

/**
 * Returns the canonical form of [domain], or `null` if [domain] is not a valid domain name.
 *
 * @param normalize lowercases and normalizes text; applied to [domain] and to every decoded Punycode label.
 */
internal fun canonicalizeDomainOrNull(
    domain: String,
    normalize: (String) -> String = { it.lowercase() }
): String? {
    if (domain.length !in 1..MAX_DOMAIN_LENGTH) return null

    val labels = normalize(domain).replaceFullStopVariants().split('.')
    val canonicalLabels = labels.map { canonicalizeLabelOrNull(it, normalize) ?: return null }
    val canonicalDomain = canonicalLabels.joinToString(".")

    return canonicalDomain.takeIf { it.length <= MAX_DOMAIN_LENGTH }
}

/**
 * Returns this host or cookie domain with every valid label canonicalized, for domain matching.
 * Unicode normalization is skipped, so the result is the same on every platform.
 * Invalid labels, such as `my_service`, are only lowercased; they cannot equal a canonical label.
 */
internal fun String.canonicalizeValidLabels(): String {
    val labels = lowercase().replaceFullStopVariants().split('.')
    return labels.joinToString(".") { label -> canonicalizeLabelOrNull(label) ?: label }
}

private fun canonicalizeLabelOrNull(
    label: String,
    normalize: (String) -> String = { it.lowercase() }
): String? {
    val unicode = Punycode.decode(label) ?: return null
    if (label.startsWith(Punycode.PREFIX, ignoreCase = true) && unicode.all { it.code < 0x80 }) return null

    // Decoded Punycode is normalized only here. Normalizing must not create a separator: U+2488 becomes "1."
    val normalized = normalize(unicode)
    if (normalized.any { it == '.' || it in FULL_STOP_VARIANTS }) return null
    if (normalized.hasInvisibleCharacters()) return null

    return Punycode.encode(normalized)?.takeIf { it.isValidLabel() }
}

private fun String.replaceFullStopVariants(): String =
    FULL_STOP_VARIANTS.fold(this) { result, fullStop -> result.replace(fullStop, '.') }

private fun String.isValidLabel(): Boolean =
    length in 1..MAX_LABEL_LENGTH &&
        first() != '-' &&
        last() != '-' &&
        all { it in 'a'..'z' || it in '0'..'9' || it == '-' }

// Whitespace, control, and formatting characters that UTS #46 deletes or disallows.
// A label containing one could spell a public suffix differently.
private fun String.hasInvisibleCharacters(): Boolean {
    var index = 0
    while (index < length) {
        val char = this[index]
        val next = getOrNull(index + 1)
        if (char.isHighSurrogate() && next != null && next.isLowSurrogate()) {
            if (isInvisible(((char.code - 0xD800) shl 10) + (next.code - 0xDC00) + 0x10000)) return true
            index += 2
            continue
        }
        if (char.isWhitespace() || char.category in INVISIBLE_CATEGORIES || isInvisible(char.code)) {
            return true
        }
        index++
    }
    return false
}

// Invisible characters outside the format category: the combining grapheme joiner, Hangul fillers,
// Khmer inherent vowels, variation selectors, shorthand format controls, musical formatting, and tags
private fun isInvisible(codePoint: Int): Boolean = when (codePoint) {
    0x034F, 0x3164, 0xFFA0 -> true
    in 0x115F..0x1160, in 0x17B4..0x17B5, in 0x180B..0x180F, in 0xFE00..0xFE0F -> true
    in 0x1BCA0..0x1BCA3, in 0x1D173..0x1D17A, in 0xE0000..0xE0FFF -> true
    else -> false
}
