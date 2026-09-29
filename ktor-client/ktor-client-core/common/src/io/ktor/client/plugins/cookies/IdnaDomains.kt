/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cookies

// Full stop variants that UTS #46 maps to '.': ideographic, fullwidth, and halfwidth ideographic
internal val IDNA_SEPARATORS: CharArray = charArrayOf('。', '．', '｡')

internal fun String.replaceIdnaSeparators(): String =
    IDNA_SEPARATORS.fold(this) { result, separator -> result.replace(separator, '.') }

/**
 * Converts a host or cookie domain to the ASCII form used for domain matching:
 * lowercase, with IDNA full stops mapped to `.`, and non-ASCII labels Punycode-encoded.
 *
 * Unlike the public-suffix canonicalization, this applies no Unicode normalization,
 * so it behaves the same on every platform.
 *
 * @return the ASCII form, or `null` when the value cannot be encoded, for example because of an unpaired surrogate.
 */
internal fun String.toAsciiDomainOrNull(): String? = Punycode.encode(lowercase().replaceIdnaSeparators())
