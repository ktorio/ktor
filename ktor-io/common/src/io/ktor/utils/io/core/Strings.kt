package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*

@Suppress("NOTHING_TO_INLINE")
inline fun String.toByteArray(charset: Charset = Charsets.UTF_8): ByteArray =
    charset.newEncoder().encodeToByteArray(this, 0, length)

/**
 * Create an instance of [String] from the specified [bytes] range starting at [offset] and bytes [length]
 * interpreting characters in the specified [charset].
 */
@Suppress("FunctionName")
expect fun String(
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
    charset: Charset = Charsets.UTF_8
): String

/**
 * Read a string line considering optionally specified [estimate] but up to optional [limit] characters length
 * (does fail once limit exceeded) or return `null` if the packet is empty
 */
fun ByteReadPacket.readUTF8Line(estimate: Int = 16, limit: Int = Int.MAX_VALUE): String? {
    if (isEmpty) return null
    val sb = StringBuilder(estimate)
    return if (readUTF8LineTo(sb, limit)) sb.toString() else null
}

/**
 * Read a string line considering optionally specified [estimate] but up to optional [limit] characters length
 * (does fail once limit exceeded) or return `null` if the packet is empty
 */
fun Input.readUTF8Line(estimate: Int = 16, limit: Int = Int.MAX_VALUE): String? {
    val sb = StringBuilder(estimate)
    return if (readUTF8LineTo(sb, limit)) sb.toString() else null
}

/**
 * Read UTF-8 line and append all line characters to [out] except line endings. Does support CR, LF and CR+LF
 * @return `true` if some characters were appended or line ending reached (empty line) or `false` if packet
 * if empty
 */
fun Input.readUTF8LineTo(out: Appendable, limit: Int): Boolean {
    var decoded = 0
    var size = 1
    var cr = false
    var end = false

    takeWhileSize { buffer ->
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

                    if (decoded == limit) bufferLimitExceeded(limit)
                    decoded++
                    out.append(ch)
                    true
                }
            }
        }

        if (skip > 0) {
            buffer.discardExact(skip)
        }

        if (end) 0 else size.coerceAtLeast(1)
    }

    if (size > 1) prematureEndOfStream(size)

    return decoded > 0 || !endOfInput
}

/**
 * Reads UTF-8 characters until one of the specified [delimiters] found, [limit] exceeded or end of stream encountered
 *
 * @throws BufferLimitExceededException
 * @returns a string of characters read before delimiter
 */
fun Input.readUTF8UntilDelimiter(delimiters: String, limit: Int = Int.MAX_VALUE): String {
    return buildString {
        readUTF8UntilDelimiterTo(this, delimiters, limit)
    }
}

/**
 * Reads UTF-8 characters to [out] buffer until one of the specified [delimiters] found, [limit] exceeded
 * or end of stream encountered
 *
 * @throws BufferLimitExceededException
 * @returns number of characters copied (possibly zero)
 */
fun Input.readUTF8UntilDelimiterTo(out: Appendable, delimiters: String, limit: Int = Int.MAX_VALUE): Int {
    var decoded = 0
    var delimiter = false

    takeWhile { buffer ->
        buffer.decodeASCII { ch ->
            if (ch in delimiters) {
                delimiter = true
                false
            } else {
                if (decoded == limit) bufferLimitExceeded(limit)
                decoded++
                out.append(ch)
                true
            }
        }
    }

    if (!delimiter) {
        decoded = readUTF8UntilDelimiterToSlowUtf8(out, delimiters, limit, decoded)
    }

    return decoded
}

/**
 * Reads UTF-8 characters to [out] buffer until one of the specified [delimiters] found, [limit] exceeded
 * or end of stream encountered
 *
 * @throws BufferLimitExceededException
 * @returns number of characters copied (possibly zero)
 */
fun Input.readUTF8UntilDelimiterTo(out: Output, delimiters: String, limit: Int = Int.MAX_VALUE): Int {
    val delimitersCount = delimiters.length
    if (delimitersCount == 1 && delimiters[0].isAsciiChar()) {
        return readUntilDelimiter(delimiters[0].toByte(), out).toInt()
    } else if (delimitersCount == 2 && delimiters[0].isAsciiChar() && delimiters[1].isAsciiChar()) {
        return readUntilDelimiters(delimiters[0].toByte(), delimiters[1].toByte(), out).toInt()
    }

    return readUTFUntilDelimiterToSlowAscii(delimiters, limit, out)
}

@Suppress("unused", "DEPRECATION_ERROR")
@Deprecated("Use Output version instead", level = DeprecationLevel.HIDDEN)
fun Input.readUTF8UntilDelimiterTo(out: BytePacketBuilderBase, delimiters: String, limit: Int = Int.MAX_VALUE): Int {
    return readUTF8UntilDelimiterTo(out as Output, delimiters, limit)
}

/**
 * Read exactly [n] bytes (consumes all remaining if [n] is not specified but up to [Int.MAX_VALUE] bytes).
 * Does fail if not enough bytes remaining.
 */
fun ByteReadPacket.readBytes(
    n: Int = remaining.coerceAtMostMaxIntOrFail("Unable to convert to a ByteArray: packet is too big")
): ByteArray = when {
    n != 0 -> ByteArray(n).also { readFully(it, 0, n) }
    else -> EmptyByteArray
}

/**
 * Reads exactly [n] bytes from the input or fails if not enough bytes available.
 */
fun Input.readBytes(n: Int): ByteArray = readBytesOf(n, n)

/**
 * Reads all remaining bytes from the input
 */
fun Input.readBytes(): ByteArray = readBytesOf()

/**
 * Reads at least [min] but no more than [max] bytes from the input to a new byte array
 * @throws EOFException if not enough bytes available to get [min] bytes
 */
fun Input.readBytesOf(min: Int = 0, max: Int = Int.MAX_VALUE): ByteArray = if (min == max && min == 0) {
    EmptyByteArray
} else if (min == max) {
    ByteArray(min).also { readFully(it, 0, min) }
} else {
    var array = ByteArray(max.toLong().coerceAtMost(sizeEstimate()).coerceAtLeast(min.toLong()).toInt())
    var size = 0

    while (size < max) {
        val partSize = minOf(max, array.size) - size
        val rc = readAvailable(array, size, partSize)
        if (rc <= 0) break
        size += rc
        if (array.size == size) {
            array = array.copyOf(size * 2)
        }
    }

    if (size < min) {
        throw EOFException("Not enough bytes available to read $min bytes: ${min - size} more required")
    }

    if (size == array.size) array else array.copyOf(size)
}

/**
 * Reads at most [max] characters decoding bytes with specified [decoder]. Extra character bytes will remain unconsumed
 * @return number of characters copied to [out]
 */
@Deprecated(
    "Use CharsetDecoder.decode instead",
    ReplaceWith("decoder.decode(this, out, max)", "io.ktor.utils.io.charsets.decode"),
    level = DeprecationLevel.ERROR
)
fun Input.readText(out: Appendable, decoder: CharsetDecoder, max: Int = Int.MAX_VALUE): Int {
    return decoder.decode(this, out, max)
}

/**
 * Reads at most [max] characters decoding bytes with specified [charset]. Extra character bytes will remain unconsumed
 * @return number of characters copied to [out]
 */
fun Input.readText(out: Appendable, charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): Int {
    return charset.newDecoder().decode(this, out, max)
}

/**
 * Reads at most [max] characters decoding bytes with specified [decoder]. Extra character bytes will remain unconsumed
 * @return a decoded string
 */
@Deprecated(
    "Use CharsetDecoder.decode instead",
    ReplaceWith("decoder.decode(this, max)", "io.ktor.utils.io.charsets.decode")
)
fun Input.readText(decoder: CharsetDecoder, max: Int = Int.MAX_VALUE): String {
    return decoder.decode(this, max)
}

/**
 * Reads at most [max] characters decoding bytes with specified [charset]. Extra character bytes will remain unconsumed
 * @return a decoded string
 */
fun Input.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String {
    return charset.newDecoder().decode(this, max)
}

/**
 * Reads at most [max] characters decoding bytes with specified [charset]. Extra character bytes will remain unconsumed
 * @return a decoded string
 */
fun Buffer.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String = buildString {
    charset.newDecoder().decodeBuffer(this@readText, this, true, max)
}

/**
 * Read exactly [n] characters interpreting bytes in the specified [charset].
 */
@Deprecated(
    "Use readTextExactCharacters instead.",
    ReplaceWith("readTextExactCharacters(n, charset)")
)
fun Input.readTextExact(charset: Charset = Charsets.UTF_8, n: Int): String {
    return readTextExactCharacters(n, charset)
}

/**
 * Read exactly [charactersCount] characters interpreting bytes in the specified [charset].
 */
fun Input.readTextExactCharacters(charactersCount: Int, charset: Charset = Charsets.UTF_8): String {
    val s = readText(charset, charactersCount)
    if (s.length < charactersCount) {
        prematureEndOfStreamToReadChars(charactersCount)
    }
    return s
}

/**
 * Read exactly the specified number of [bytes]
 * interpreting bytes in the specified [charset] (optional, UTF-8 by default).
 */
@Deprecated("Parameters order is changed.", ReplaceWith("readTextExactBytes(bytes, charset)"))
fun Input.readTextExactBytes(charset: Charset = Charsets.UTF_8, bytes: Int): String {
    return readTextExactBytes(bytes, charset)
}

/**
 * Read exactly [bytesCount] interpreting bytes in the specified [charset] (optional, UTF-8 by default).
 */
fun Input.readTextExactBytes(bytesCount: Int, charset: Charset = Charsets.UTF_8): String {
    return charset.newDecoder().decodeExactBytes(this, inputLength = bytesCount)
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [encoder]
 */
@Deprecated(
    "Use the implementation with Charset instead",
    ReplaceWith("writeText(text, fromIndex, toIndex, encoder.charset)", "io.ktor.utils.io.charsets.charset"),
    level = DeprecationLevel.ERROR
)
fun Output.writeText(text: CharSequence, fromIndex: Int = 0, toIndex: Int = text.length, encoder: CharsetEncoder) {
    encoder.encodeToImpl(this, text, fromIndex, toIndex)
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 */
fun Output.writeText(
    text: CharSequence,
    fromIndex: Int = 0,
    toIndex: Int = text.length,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        return writeTextUtf8(text, fromIndex, toIndex)
    }

    charset.newEncoder().encodeToImpl(this, text, fromIndex, toIndex)
}

/**
 * Writes [text] characters in range \[[fromIndex] .. [toIndex]) with the specified [charset]
 */
fun Output.writeText(
    text: CharArray,
    fromIndex: Int = 0,
    toIndex: Int = text.size,
    charset: Charset = Charsets.UTF_8
) {
    if (charset === Charsets.UTF_8) {
        return writeTextUtf8(CharArraySequence(text, 0, text.size), fromIndex, toIndex)
    }

    charset.newEncoder().encode(text, fromIndex, toIndex, this)
}

private fun Output.writeTextUtf8(text: CharSequence, fromIndex: Int, toIndex: Int) {
    var index = fromIndex
    writeWhileSize(1) { buffer ->
        val memory = buffer.memory
        val dstOffset = buffer.writePosition
        val dstLimit = buffer.limit

        val (characters, bytes) = memory.encodeUTF8(text, index, toIndex, dstOffset, dstLimit)

        index += characters.toInt()
        buffer.commitWritten(bytes.toInt())

        when {
            characters.toInt() == 0 && index < toIndex -> 8
            index < toIndex -> 1
            else -> 0
        }
    }
}

internal expect fun String.getCharsInternal(dst: CharArray, dstOffset: Int)


@Suppress("NOTHING_TO_INLINE")
private inline fun Char.isAsciiChar() = toInt() <= 0x7f

private fun Input.readUTFUntilDelimiterToSlowAscii(delimiters: String, limit: Int, out: Output): Int {
    var decoded = 0
    var delimiter = false

    takeWhile { buffer ->
        val before = buffer.readRemaining

        val rc = buffer.decodeASCII { ch ->
            if (ch in delimiters) {
                delimiter = true
                false
            } else {
                if (decoded == limit) bufferLimitExceeded(limit)
                decoded++
                true
            }
        }

        val delta = before - buffer.readRemaining
        if (delta > 0) {
            buffer.rewind(delta)
            out.writeFully(buffer, delta)
        }

        rc
    }

    if (!delimiter && !endOfInput) {
        decoded = readUTF8UntilDelimiterToSlowUtf8(out, delimiters, limit, decoded)
    }

    return decoded
}

private fun Input.readUTF8UntilDelimiterToSlowUtf8(
    out: Output,
    delimiters: String,
    limit: Int,
    decoded0: Int
): Int {
    var decoded = decoded0
    var size = 1

    takeWhileSize { buffer ->
        val before = buffer.readRemaining

        size = buffer.decodeUTF8 { ch ->
            if (ch in delimiters) {
                false
            } else {
                if (decoded == limit) {
                    bufferLimitExceeded(limit)
                }
                decoded++
                true
            }
        }

        val delta = before - buffer.readRemaining
        if (delta > 0) {
            buffer.rewind(delta)
            out.writeFully(buffer, delta)
        }

        size = if (size == -1) 0 else size.coerceAtLeast(1)
        size
    }

    if (size > 1) prematureEndOfStream(size)

    return decoded
}

private fun Input.readUTF8UntilDelimiterToSlowUtf8(
    out: Appendable,
    delimiters: String,
    limit: Int,
    decoded0: Int
): Int {
    var decoded = decoded0
    var size = 1

    takeWhileSize { buffer ->
        size = buffer.decodeUTF8 { ch ->
            if (ch in delimiters) {
                false
            } else {
                if (decoded == limit) {
                    bufferLimitExceeded(limit)
                }
                decoded++
                out.append(ch)
                true
            }
        }

        size = if (size == -1) 0 else size.coerceAtLeast(1)
        size
    }

    if (size > 1) prematureEndOfStream(size)

    return decoded
}

private fun bufferLimitExceeded(limit: Int): Nothing {
    throw BufferLimitExceededException("Too many characters before delimiter: limit $limit exceeded")
}

@PublishedApi
internal fun prematureEndOfStream(size: Int): Nothing =
    throw EOFException("Premature end of stream: expected $size bytes")

@PublishedApi
internal fun prematureEndOfStream(size: Long): Nothing =
    throw EOFException("Premature end of stream: expected $size bytes")

private fun prematureEndOfStreamToReadChars(charactersCount: Int): Nothing =
    throw EOFException("Not enough input bytes to read $charactersCount characters.")
