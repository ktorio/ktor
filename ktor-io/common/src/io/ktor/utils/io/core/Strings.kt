/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// For ABI compatibility
@file:JvmMultifileClass
@file:JvmName("StringsKt")

package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*
import kotlinx.io.*
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

public fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray {
    if (charset == Charsets.UTF_8) return encodeToByteArray()

    return charset.newEncoder().encodeToByteArray(this, 0, length)
}

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.String)
 */
@Deprecated(
    "Use decodeToString instead",
    ReplaceWith("bytes.decodeToString(offset, offset + length)"),
    DeprecationLevel.WARNING
)
public fun String(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
    charset: Charset = Charsets.UTF_8
): String = when (charset) {
    Charsets.UTF_8 -> bytes.decodeToString(offset, offset + length)

    else -> buildPacket {
        writeFully(bytes, offset, length)
    }.readText(charset)
}

/**
 * Read exactly [n] bytes (consumes all remaining if [n] is not specified but up to [Int.MAX_VALUE] bytes).
 * Does fail if not enough bytes remaining.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.readBytes)
 */
@Deprecated(
    "Use readByteArray instead",
    ReplaceWith("this.readByteArray()", "kotlinx.io.readByteArray"),
)
public fun Source.readBytes(): ByteArray = readByteArray()

@Deprecated(
    "Use readByteArray instead",
    ReplaceWith("this.readByteArray(count)"),
)
public fun Source.readBytes(count: Int): ByteArray = readByteArray(count)

/**
 * Reads at most [max] bytes with specified [charset]. Extra character bytes will remain unconsumed
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.readText)
 *
 * @return a decoded string
 */
public expect fun Source.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String

/**
 * Read exactly [n] characters interpreting bytes in the specified [charset].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.readTextExact)
 */
@Deprecated(
    "Use readTextExactCharacters instead.",
    ReplaceWith("readTextExactCharacters(n, charset)")
)
public fun Source.readTextExact(charset: Charset = Charsets.UTF_8, n: Int): String {
    return readTextExactCharacters(n, charset)
}

/**
 * Read exactly [charactersCount] characters interpreting bytes in the specified [charset].
 *
 * @throws IllegalArgumentException if [charset] is not either ISO_8859_1 or UTF_8
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.readTextExactCharacters)
 */
public fun Source.readTextExactCharacters(charactersCount: Int, charset: Charset = Charsets.UTF_8): String {
    require(charactersCount >= 0) { "charactersCount shouldn't be negative: $charactersCount" }
    if (charactersCount == 0) return ""

    return when (charset) {
        Charsets.UTF_8 -> readUtf8ExactCharacters(charactersCount)
        Charsets.ISO_8859_1 -> readIso88591ExactCharacters(charactersCount)
        else -> throw IllegalArgumentException("Unsupported charset: $charset")
    }
}

private fun Source.readIso88591ExactCharacters(charactersCount: Int): String {
    val result = CharArray(charactersCount)
    repeat(charactersCount) { index ->
        val b = readByteOrFail()
        result[index] = (b.toInt() and 0xFF).toChar()
    }
    return result.concatToString()
}

private fun Source.readUtf8ExactCharacters(charactersCount: Int): String {
    val out = StringBuilder(charactersCount)
    var remainingUnits = charactersCount

    while (remainingUnits > 0) {
        val nextUnits = peekNextUtf8CodePointUtf16UnitsOrFail(charactersCount)
        if (nextUnits > remainingUnits) {
            throw MalformedInputException(
                "Unable to read exactly $charactersCount UTF-16 characters: next UTF-8 code point requires $nextUnits units."
            )
        }

        val codePoint = try {
            readCodePointValue()
        } catch (_: EOFException) {
            prematureEndOfStreamToReadChars(charactersCount)
        }

        if (codePoint <= 0xFFFF) {
            out.append(codePoint.toChar())
        } else {
            val cp = codePoint - 0x10000
            val high = ((cp ushr 10) + 0xD800).toChar()
            val low = ((cp and 0x3FF) + 0xDC00).toChar()
            out.append(high)
            out.append(low)
        }

        remainingUnits -= nextUnits
    }

    return out.toString()
}

@OptIn(InternalIoApi::class)
private fun Source.peekNextUtf8CodePointUtf16UnitsOrFail(charactersCount: Int): Int {
    request(1)
    if (buffer.size < 1L) prematureEndOfStreamToReadChars(charactersCount)

    val b0 = (buffer[0].toInt() and 0xFF)
    return when {
        (b0 and 0b1000_0000) == 0 -> 1

        (b0 and 0b1110_0000) == 0b1100_0000 -> {
            request(2)
            if (buffer.size < 2L) prematureEndOfStreamToReadChars(charactersCount)
            1
        }

        (b0 and 0b1111_0000) == 0b1110_0000 -> {
            request(3)
            if (buffer.size < 3L) prematureEndOfStreamToReadChars(charactersCount)
            1
        }

        (b0 and 0b1111_1000) == 0b1111_0000 -> {
            request(4)
            if (buffer.size < 4L) prematureEndOfStreamToReadChars(charactersCount)
            2
        }

        else -> throw MalformedInputException("Invalid UTF-8 leading byte")
    }
}

private fun Source.readByteOrFail(): Byte =
    try {
        readByte()
    } catch (_: EOFException) {
        prematureEndOfStreamToReadChars(1)
    }

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.writeText)
 */
public fun Sink.writeText(
    text: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = text.length,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        return writeString(text.toString(), fromIndex, toIndex)
    }

    charset.newEncoder().encodeToImpl(this, text, fromIndex, toIndex)
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.core.writeText)
 */
public fun Sink.writeText(
    text: CharArray,
    fromIndex: Int = 0,
    toIndex: Int = text.size,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        val string = text.concatToString(fromIndex, fromIndex + toIndex)
        return writeString(string, 0, toIndex - fromIndex)
    }

    charset.newEncoder().encode(text, fromIndex, toIndex, this)
}

private fun prematureEndOfStreamToReadChars(charactersCount: Int): Nothing =
    throw EOFException("Not enough input bytes to read $charactersCount characters.")
