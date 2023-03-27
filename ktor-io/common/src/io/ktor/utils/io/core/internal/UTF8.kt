package io.ktor.utils.io.core.internal

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.jvm.*

internal inline fun Buffer.decodeASCII(consumer: (Char) -> Boolean): Boolean {
    read { memory, start, endExclusive ->
        for (index in start until endExclusive) {
            val codepoint = memory[index].toInt() and 0xff
            if (codepoint and 0x80 == 0x80 || !consumer(codepoint.toChar())) {
                discardExact(index - start)
                return false
            }
        }

        endExclusive - start
    }

    return true
}

internal suspend fun decodeUTF8LineLoopSuspend(
    out: Appendable,
    limit: Int,
    nextChunk: suspend (Int) -> Input?,
    afterRead: (Int) -> Unit
): Boolean {
    var decoded = 0
    var size = 1
    var cr = false
    var end = false

    while (!end && size != 0) {
        val chunk = nextChunk(size) ?: break
        val totalBytes = chunk.remaining

        chunk.takeWhileSize { buffer ->
            var skip = 0
            size = buffer.decodeUTF8 { ch ->
                when (ch) {
                    '\r' -> {
                        if (cr) {
                            end = true
                            return@decodeUTF8 false
                        }
                        cr = true
                        true
                    }
                    '\n' -> {
                        end = true
                        skip = 1
                        false
                    }
                    else -> {
                        if (cr) {
                            end = true
                            return@decodeUTF8 false
                        }

                        if (decoded == limit) {
                            throw TooLongLineException("Too many characters in line: limit $limit exceeded")
                        }
                        decoded++
                        out.append(ch)
                        true
                    }
                }
            }

            if (skip > 0) {
                buffer.discardExact(skip)
            }

            size = if (end) 0 else size.coerceAtLeast(1)

            size
        }

        afterRead((totalBytes - chunk.remaining).toInt())
    }

    if (size > 1) prematureEndOfStreamUtf(size)
    if (cr) {
        end = true
    }

    return decoded > 0 || end
}

private fun prematureEndOfStreamUtf(size: Int): Nothing =
    throw EOFException("Premature end of stream: expected $size bytes to decode UTF-8 char")

internal fun byteCountUtf8(firstByte: Int): Int {
    var byteCount = 0
    var mask = 0x80
    var value = firstByte

    for (i in 1..6) {
        if (value and mask != 0) {
            value = value and mask.inv()
            mask = mask shr 1
            byteCount++
        } else {
            break
        }
    }

    return byteCount
}

/**
 * Decodes all the bytes to utf8 applying every character on [consumer] until or consumer return `false`.
 * If a consumer returned false then a character will be pushed back (including all surrogates will be pushed back as well)
 * and [decodeUTF8] returns -1
 * @return number of bytes required to decode incomplete utf8 character or 0 if all bytes were processed
 * or -1 if consumer rejected loop
 */
internal inline fun Buffer.decodeUTF8(consumer: (Char) -> Boolean): Int {
    var byteCount = 0
    var value = 0
    var lastByteCount = 0

    read { memory, start, endExclusive ->
        for (index in start until endExclusive) {
            val v = memory[index].toInt() and 0xff
            when {
                v and 0x80 == 0 -> {
                    if (byteCount != 0) malformedByteCount(byteCount)
                    if (!consumer(v.toChar())) {
                        discardExact(index - start)
                        return -1
                    }
                }
                byteCount == 0 -> {
                    // first unicode byte

                    var mask = 0x80
                    value = v

                    for (i in 1..6) { // TODO do we support 6 bytes unicode?
                        if (value and mask != 0) {
                            value = value and mask.inv()
                            mask = mask shr 1
                            byteCount++
                        } else {
                            break
                        }
                    }

                    lastByteCount = byteCount
                    byteCount--

                    if (lastByteCount > endExclusive - index) {
                        discardExact(index - start)
                        return lastByteCount
                    }
                }
                else -> {
                    // trailing unicode byte
                    value = (value shl 6) or (v and 0x7f)
                    byteCount--

                    if (byteCount == 0) {
                        if (isBmpCodePoint(value)) {
                            if (!consumer(value.toChar())) {
                                discardExact(index - start - lastByteCount + 1)
                                return -1
                            }
                        } else if (!isValidCodePoint(value)) {
                            malformedCodePoint(value)
                        } else {
                            if (!consumer(highSurrogate(value).toChar()) ||
                                !consumer(lowSurrogate(value).toChar())
                            ) {
                                discardExact(index - start - lastByteCount + 1)
                                return -1
                            }
                        }

                        value = 0
                    }
                }
            }
        }

        endExclusive - start
    }

    return 0
}

internal fun Memory.encodeUTF8(text: CharSequence, from: Int, to: Int, dstOffset: Int, dstLimit: Int): EncodeResult {
    // encode single-byte characters
    val lastCharIndex = minOf(to, from + UShort.MAX_VALUE.toInt())
    val resultLimit = dstLimit.coerceAtMost(UShort.MAX_VALUE.toInt())
    var resultPosition = dstOffset
    var index = from

    do {
        if (resultPosition >= resultLimit || index >= lastCharIndex) {
            return EncodeResult((index - from).toUShort(), (resultPosition - dstOffset).toUShort())
        }

        val character = text[index++].code and 0xffff
        if (character and 0xff80 == 0) {
            storeAt(resultPosition++, character.toByte())
        } else {
            break
        }
    } while (true)

    index--
    return encodeUTF8Stage1(text, index, lastCharIndex, from, resultPosition, resultLimit, dstOffset)
}

/**
 * Encode UTF-8 multibytes characters when we for sure have enough free space
 */
private fun Memory.encodeUTF8Stage1(
    text: CharSequence,
    index1: Int,
    lastCharIndex: Int,
    from: Int,
    resultPosition1: Int,
    resultLimit: Int,
    dstOffset: Int
): EncodeResult {
    var index = index1
    var resultPosition: Int = resultPosition1
    val stage1Limit = resultLimit - 3

    do {
        val freeSpace = stage1Limit - resultPosition
        if (freeSpace <= 0 || index >= lastCharIndex) {
            break
        }

        val character = text[index++]
        val codepoint = when {
            character.isHighSurrogate() -> {
                if (index == lastCharIndex || !text[index].isLowSurrogate()) {
                    63
                } else {
                    codePoint(character, text[index++])
                }
            }
            else -> character.code
        }
        val size = putUtf8Char(resultPosition, codepoint)

        resultPosition += size
    } while (true)

    if (resultPosition == stage1Limit) {
        return encodeUTF8Stage2(text, index, lastCharIndex, from, resultPosition, resultLimit, dstOffset)
    }

    return EncodeResult((index - from).toUShort(), (resultPosition - dstOffset).toUShort())
}

private fun Memory.encodeUTF8Stage2(
    text: CharSequence,
    index1: Int,
    lastCharIndex: Int,
    from: Int,
    resultPosition1: Int,
    resultLimit: Int,
    dstOffset: Int
): EncodeResult {
    var index = index1
    var resultPosition: Int = resultPosition1

    do {
        val freeSpace = resultLimit - resultPosition
        if (freeSpace <= 0 || index >= lastCharIndex) {
            break
        }

        val character = text[index++]
        val codepoint = when {
            !character.isHighSurrogate() -> character.code
            else -> {
                if (index == lastCharIndex || !text[index].isLowSurrogate()) {
                    63
                } else {
                    codePoint(character, text[index++])
                }
            }
        }
        if (charactersSize(codepoint) > freeSpace) {
            index--
            break
        }
        val size = putUtf8Char(resultPosition, codepoint)
        resultPosition += size
    } while (true)

    return EncodeResult((index - from).toUShort(), (resultPosition - dstOffset).toUShort())
}

@Suppress("NOTHING_TO_INLINE")
private inline fun charactersSize(v: Int) = when (v) {
    in 1..0x7f -> 1
    in 0x80..0x7ff -> 2
    in 0x800..0xffff -> 3
    in 0x10000..0x10ffff -> 4
    else -> malformedCodePoint(v)
}

// TODO optimize it, now we are simply do naive encoding here
@Suppress("NOTHING_TO_INLINE")
internal inline fun Memory.putUtf8Char(offset: Int, v: Int): Int = when (v) {
    in 0..0x7f -> {
        storeAt(offset, v.toByte())
        1
    }
    in 0x80..0x7ff -> {
        this[offset] = (0xc0 or ((v shr 6) and 0x1f)).toByte()
        this[offset + 1] = (0x80 or (v and 0x3f)).toByte()
        2
    }
    in 0x800..0xffff -> {
        this[offset] = (0xe0 or ((v shr 12) and 0x0f)).toByte()
        this[offset + 1] = (0x80 or ((v shr 6) and 0x3f)).toByte()
        this[offset + 2] = (0x80 or (v and 0x3f)).toByte()
        3
    }
    in 0x10000..0x10ffff -> {
        this[offset] = (0xf0 or ((v shr 18) and 0x07)).toByte() // 3 bits
        this[offset + 1] = (0x80 or ((v shr 12) and 0x3f)).toByte() // 6 bits
        this[offset + 2] = (0x80 or ((v shr 6) and 0x3f)).toByte() // 6 bits
        this[offset + 3] = (0x80 or (v and 0x3f)).toByte() // 6 bits
        4
    }
    else -> malformedCodePoint(v)
}

@PublishedApi
internal fun malformedByteCount(byteCount: Int): Nothing =
    throw MalformedUTF8InputException("Expected $byteCount more character bytes")

@PublishedApi
internal fun malformedCodePoint(value: Int): Nothing =
    throw IllegalArgumentException("Malformed code-point $value found")

private const val MaxCodePoint = 0x10ffff
private const val MinLowSurrogate = 0xdc00
private const val MinHighSurrogate = 0xd800
private const val MinSupplementary = 0x10000
private const val HighSurrogateMagic = MinHighSurrogate - (MinSupplementary ushr 10)

@PublishedApi
internal fun isBmpCodePoint(cp: Int): Boolean = cp ushr 16 == 0

@PublishedApi
internal fun isValidCodePoint(codePoint: Int): Boolean = codePoint <= MaxCodePoint

@PublishedApi
internal fun lowSurrogate(cp: Int): Int = (cp and 0x3ff) + MinLowSurrogate

@PublishedApi
internal fun highSurrogate(cp: Int): Int = (cp ushr 10) + HighSurrogateMagic

internal fun codePoint(high: Char, low: Char): Int {
    val highValue = high.code - HighSurrogateMagic
    val lowValue = low.code - MinLowSurrogate

    return highValue shl 10 or lowValue
}

public class MalformedUTF8InputException(message: String) : Exception(message)
