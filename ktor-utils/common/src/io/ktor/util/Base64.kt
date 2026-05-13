/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import io.ktor.utils.io.core.*
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlin.io.encoding.Base64

/**
 * Encode [String] in base64 format and UTF-8 character encoding.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.encodeBase64)
 */
@Deprecated(
    "Use `Base64.Default.encode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Base64.encode(encodeToByteArray())", "kotlin.io.encoding.Base64")
)
public fun String.encodeBase64(): String = Base64.encode(encodeToByteArray())

/**
 * Encode [ByteArray] in base64 format
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.encodeBase64)
 */
@Deprecated(
    "Use `Base64.Default.encode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Base64.encode(this)", "kotlin.io.encoding.Base64")
)
public fun ByteArray.encodeBase64(): String = Base64.encode(this)

/**
 * Encode [ByteReadPacket] in base64 format
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.encodeBase64)
 */
@Deprecated(
    "Use `Base64.Default.encode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Base64.encode(readByteArray())", "kotlin.io.encoding.Base64")
)
public fun Source.encodeBase64(): String = Base64.encode(readByteArray())

/**
 * Decode [String] from base64 format encoded in UTF-8.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.decodeBase64String)
 */
@Deprecated(
    "Ambiguous and lenient. Use `Base64.Default.decode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Base64.decode(this).decodeToString()", "kotlin.io.encoding.Base64")
)
public fun String.decodeBase64String(): String = decodeBase64Bytes().decodeToString()

/**
 * Decode [String] from base64 format with optional padding
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.decodeBase64Bytes)
 */
@Deprecated(
    "Ambiguous and lenient. Use `Base64.Default.decode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("Base64.decode(this)", "kotlin.io.encoding.Base64")
)
public fun String.decodeBase64Bytes(): ByteArray =
    runCatching { Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(this) }
        .getOrElse { Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(this) }

/**
 * Decode [ByteReadPacket] from base64 format with optional padding
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.decodeBase64Bytes)
 */
@Deprecated(
    "Ambiguous and lenient. Use `Base64.Default.decode()` from the Kotlin standard library.",
    level = DeprecationLevel.WARNING,
    replaceWith = ReplaceWith("buildPacket { writeFully(Base64.decode(readByteArray())) }", "kotlin.io.encoding.Base64")
)
public fun Source.decodeBase64Bytes(): Input = buildPacket {
    val raw = readByteArray()
    val decoded = runCatching { Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(raw) }
        .getOrElse { Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL).decode(raw) }
    writeFully(decoded)
}
