package io.ktor.utils.io.charsets

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import java.nio.*

@DangerousInternalIoApi
fun decodeUtf8Result(numberOfChars: Int, requireBytes: Int): Long =
    numberOfChars.toLong() shl 32 or (requireBytes.toLong() and 0xffffffffL)

internal fun decodeUtf8ResultAcc(predecoded: Int, result: Long): Long =
    decodeUtf8Result(predecoded + (result shr 32).toInt(), (result and 0xffffffffL).toInt())

@DangerousInternalIoApi
fun decodeUtf8ResultCombine(prev: Long, next: Long): Long =
    ((prev and 0xffffffffL.inv()) + (next and 0xffffffffL.inv())) or (next and 0xffffffffL)

@ExperimentalIoApi
fun ByteBuffer.decodeUTF(out: CharArray, offset: Int, length: Int): Long {
    val decoded = decodeASCII(out, offset, length)

    return when {
        !hasRemaining() || decoded == length -> decodeUtf8Result(decoded, 0)
        hasArray() -> decodeUtf8ResultAcc(decoded, decodeUTF8_array(out, offset + decoded, length - decoded))
        else -> decodeUtf8ResultAcc(decoded, decodeUTF8_buffer(out, offset + decoded, length - decoded))
    }
}

/**
 * @return packed number of decoded characters to [out] buffer (highest 32 bits) and number of bytes required
 * to decode the next character, or 0 if buffer has been decoded completely or -1 if end of line has been encountered
 */
@ExperimentalIoApi
fun ByteBuffer.decodeUTF8Line(out: CharArray, offset: Int = 0, length: Int = out.size): Long {
    return when {
        hasArray() -> decodeUTF8Line_array(out, offset, length)
        else -> decodeUTF8Line_buffer(out, offset, length)
    }
}

private fun ByteBuffer.decodeUTF8Line_array(out: CharArray, offset: Int, length: Int): Long {
    var cr = false

    val rc = decodeUTF8_array(out, offset, length) { ch ->
        when {
            ch == '\r' -> {
                cr = true
                true
            }
            ch == '\n' -> {
                cr = false
                false
            }
            cr -> {
                false
            }
            else -> true
        }
    }

    val required = (rc and 0xffffffffL).toInt()

    if (required == -1) {
        val decoded = (rc shr 32).toInt()
        // found EOL
        if (cr) {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR
        }

        position(position() + 1) // consume LF
        if (decoded > 0 && out[decoded - 1] == '\r') {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR followed by LF
        }
    } else if (required == 0 && cr) {
        // got CR but the next character is unknown
        val decoded = (rc shr 32).toInt()
        position(position() - 1) // push back CR
        return decodeUtf8Result(decoded - 1, 2) // 2 because we need CR + one more character
    }

    return rc
}

private fun ByteBuffer.decodeUTF8Line_buffer(out: CharArray, offset: Int, length: Int): Long {
    var cr = false

    val rc = decodeUTF8_buffer(out, offset, length) { ch ->
        when {
            ch == '\r' -> {
                cr = true
                true
            }
            ch == '\n' -> {
                cr = false
                false
            }
            cr -> {
                false
            }
            else -> true
        }
    }

    val required = (rc and 0xffffffffL).toInt()

    if (required == -1) {
        val decoded = (rc shr 32).toInt()
        // found EOL
        if (cr) {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR
        }

        position(position() + 1) // consume LF
        if (decoded > 0 && out[decoded - 1] == '\r') {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR followed by LF
        }
    } else if (required == 0 && cr) {
        // got CR but the next character is unknown
        val decoded = (rc shr 32).toInt()
        position(position() - 1) // push back CR
        return decodeUtf8Result(decoded - 1, 2) // 2 because we need CR + one more character
    }

    return rc
}

/**
 * @return number of bytes decoded to [out] and number of required bytes.
 * @see [decodeUtf8Result]
 */
private fun ByteBuffer.decodeUTF8_array(out: CharArray, offset: Int, length: Int): Long {
    val array = array()!!
    var srcPos = arrayOffset() + position()
    val srcEnd = srcPos + remaining()

    require(srcPos <= srcEnd)
    require(srcEnd <= array.size)

    var outPos = offset
    val outEnd = offset + length

    if (outEnd > out.size) throw indexOutOfBounds(offset, length, out.size)

    while (srcPos < srcEnd && outPos < outEnd) {
        val v = array[srcPos++]
        val vi = v.toInt()

        when {
            v >= 0 -> {
                val ch = v.toChar()
                out[outPos++] = ch
            }
            vi and 0xe0 == 0xc0 -> {
                // 2 bytes, always valid

                if (srcPos >= srcEnd) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 2)
                }

                val second = array[srcPos++].toInt()
                out[outPos++] = ((vi and 0x1f shl 6) or (second and 0x3f)).toChar()
            }
            vi and 0xf0 == 0xe0 -> {
                // 3 bytes
                if (srcEnd - srcPos < 2) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 3)
                }

                val second = array[srcPos++].toInt()
                val third = array[srcPos++].toInt()

                val highest = vi and 0x0f
                val vv = ((highest shl 12) or (second and 0x3f shl 6) or (third and 0x3f))

                if (highest == 0 || isBmpCodePoint(vv)) {
                    out[outPos++] = vv.toChar()
                } else {
                    malformedCodePoint(vv)
                }
            }
            vi and 0xf8 == 0xf0 -> {
                // 4 bytes

                if (srcEnd - srcPos < 3) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 4)
                }

                val second = array[srcPos++].toInt()
                val third = array[srcPos++].toInt()
                val fourth = array[srcPos++].toInt()

                val vv =
                    ((vi and 0x07 shl 18) or (second and 0x3f shl 12) or (third and 0x3f shl 6) or (fourth and 0x3f))

                if (!isValidCodePoint(vv)) {
                    malformedCodePoint(vv)
                } else if (outEnd - outPos >= 2) {
                    val high = highSurrogate(vv)
                    val low = lowSurrogate(vv)

                    out[outPos++] = high.toChar()
                    out[outPos++] = low.toChar()
                } else {
                    position(srcPos - 4)
                    return decodeUtf8Result(outPos - offset, 0)
                }
            }
            else -> unsupportedByteCount(v)
        }
    }

    position(srcPos)

    return decodeUtf8Result(outPos - offset, 0)
}

/**
 * @return number of bytes decoded to [out] and number of required bytes.
 * @see [decodeUtf8Result]
 */
private fun ByteBuffer.decodeUTF8_buffer(out: CharArray, offset: Int, length: Int): Long {
    var outPos = offset
    val outEnd = offset + length

    if (outEnd > out.size) throw indexOutOfBounds(offset, length, out.size)

    while (hasRemaining() && outPos < outEnd) {
        val v = get()
        val vi = v.toInt()

        when {
            v >= 0 -> {
                val ch = v.toChar()
                out[outPos++] = ch
            }
            vi and 0xe0 == 0xc0 -> {
                // 2 bytes, always valid

                if (hasRemaining()) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 2)
                }

                val second = get().toInt()
                out[outPos++] = ((vi and 0x1f shl 6) or (second and 0x3f)).toChar()
            }
            vi and 0xf0 == 0xe0 -> {
                // 3 bytes
                if (remaining() < 2) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 3)
                }

                val second = get().toInt()
                val third = get().toInt()

                val highest = vi and 0x0f
                val vv = ((highest shl 12) or (second and 0x3f shl 6) or (third and 0x3f))

                if (highest == 0 || isBmpCodePoint(vv)) {
                    out[outPos++] = vv.toChar()
                } else {
                    malformedCodePoint(vv)
                }
            }
            vi and 0xf8 == 0xf0 -> {
                // 4 bytes

                if (remaining() < 3) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 4)
                }

                val second = get().toInt()
                val third = get().toInt()
                val fourth = get().toInt()

                val vv =
                    ((vi and 0x07 shl 18) or (second and 0x3f shl 12) or (third and 0x3f shl 6) or (fourth and 0x3f))

                if (!isValidCodePoint(vv)) {
                    malformedCodePoint(vv)
                } else if (outEnd - outPos >= 2) {
                    val high = highSurrogate(vv)
                    val low = lowSurrogate(vv)

                    out[outPos++] = high.toChar()
                    out[outPos++] = low.toChar()
                } else {
                    position(position() - 4)
                    return decodeUtf8Result(outPos - offset, 0)
                }
            }
            else -> unsupportedByteCount(v)
        }
    }

    return decodeUtf8Result(outPos - offset, 0)
}


/**
 * @return number of bytes decoded to [out] and number of required bytes.
 * @see [decodeUtf8Result]
 */
private inline fun ByteBuffer.decodeUTF8_array(
    out: CharArray,
    offset: Int,
    length: Int,
    predicate: (Char) -> Boolean
): Long {
    val array = array()!!
    var srcPos = arrayOffset() + position()
    val srcEnd = srcPos + remaining()

    require(srcPos <= srcEnd)
    require(srcEnd <= array.size)

    var outPos = offset
    val outEnd = offset + length

    if (outEnd > out.size) throw indexOutOfBounds(offset, length, out.size)

    while (srcPos < srcEnd && outPos < outEnd) {
        val v = array[srcPos++]
        val vi = v.toInt()

        when {
            v >= 0 -> {
                val ch = v.toChar()
                if (!predicate(ch)) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, -1)
                }
                out[outPos++] = ch
            }
            vi and 0xe0 == 0xc0 -> {
                // 2 bytes, always valid

                if (srcPos >= srcEnd) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 2)
                }

                val second = array[srcPos++].toInt()
                val ch = ((vi and 0x1f shl 6) or (second and 0x3f)).toChar()

                if (!predicate(ch)) {
                    position(srcPos - 2)
                    return decodeUtf8Result(outPos - offset, -1)
                }

                out[outPos++] = ch
            }
            vi and 0xf0 == 0xe0 -> {
                // 3 bytes
                if (srcEnd - srcPos < 2) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 3)
                }

                val second = array[srcPos++].toInt()
                val third = array[srcPos++].toInt()

                val highest = vi and 0x0f
                val vv = ((highest shl 12) or (second and 0x3f shl 6) or (third and 0x3f))

                if (highest == 0 || isBmpCodePoint(vv)) {
                    val ch = vv.toChar()

                    if (!predicate(ch)) {
                        position(srcPos - 4)
                        return decodeUtf8Result(outPos - offset, -1)
                    }

                    out[outPos++] = ch
                } else {
                    malformedCodePoint(vv)
                }
            }
            vi and 0xf8 == 0xf0 -> {
                // 4 bytes

                if (srcEnd - srcPos < 3) {
                    position(srcPos - 1)
                    return decodeUtf8Result(outPos - offset, 4)
                }

                val second = array[srcPos++].toInt()
                val third = array[srcPos++].toInt()
                val fourth = array[srcPos++].toInt()

                val vv =
                    ((vi and 0x07 shl 18) or (second and 0x3f shl 12) or (third and 0x3f shl 6) or (fourth and 0x3f))

                if (!isValidCodePoint(vv)) {
                    malformedCodePoint(vv)
                } else if (outEnd - outPos >= 2) {
                    val high = highSurrogate(vv).toChar()
                    val low = lowSurrogate(vv).toChar()

                    if (!predicate(high) || !predicate(low)) {
                        position(srcPos - 4)
                        return decodeUtf8Result(outPos - offset, -1)
                    }

                    out[outPos++] = high
                    out[outPos++] = low
                } else {
                    position(srcPos - 4)
                    return decodeUtf8Result(outPos - offset, 0)
                }
            }
            else -> unsupportedByteCount(v)
        }
    }

    position(srcPos)

    return decodeUtf8Result(outPos - offset, 0)
}

/**
 * @return number of bytes decoded to [out] and number of required bytes.
 * @see [decodeUtf8Result]
 */
private inline fun ByteBuffer.decodeUTF8_buffer(
    out: CharArray,
    offset: Int,
    length: Int,
    predicate: (Char) -> Boolean
): Long {
    var outPos = offset
    val outEnd = offset + length

    if (outEnd > out.size) throw indexOutOfBounds(offset, length, out.size)

    while (hasRemaining() && outPos < outEnd) {
        val v = get()
        val vi = v.toInt()

        when {
            v >= 0 -> {
                val ch = v.toChar()
                if (!predicate(ch)) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, -1)
                }
                out[outPos++] = ch
            }
            vi and 0xe0 == 0xc0 -> {
                // 2 bytes, always valid

                if (!hasRemaining()) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 2)
                }

                val second = get().toInt()
                val ch = ((vi and 0x1f shl 6) or (second and 0x3f)).toChar()
                if (!predicate(ch)) {
                    position(position() - 2)
                    return decodeUtf8Result(outPos - offset, -1)
                }
                out[outPos++] = ch
            }
            vi and 0xf0 == 0xe0 -> {
                // 3 bytes
                if (remaining() < 2) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 3)
                }

                val second = get().toInt()
                val third = get().toInt()

                val highest = vi and 0x0f
                val vv = ((highest shl 12) or (second and 0x3f shl 6) or (third and 0x3f))

                if (highest == 0 || isBmpCodePoint(vv)) {
                    val ch = vv.toChar()
                    if (!predicate(ch)) {
                        position(position() - 3)
                        return decodeUtf8Result(outPos - offset, -1)
                    }

                    out[outPos++] = ch
                } else {
                    malformedCodePoint(vv)
                }
            }
            vi and 0xf8 == 0xf0 -> {
                // 4 bytes

                if (remaining() < 3) {
                    position(position() - 1)
                    return decodeUtf8Result(outPos - offset, 4)
                }

                val second = get().toInt()
                val third = get().toInt()
                val fourth = get().toInt()

                val vv =
                    ((vi and 0x07 shl 18) or (second and 0x3f shl 12) or (third and 0x3f shl 6) or (fourth and 0x3f))

                if (!isValidCodePoint(vv)) {
                    malformedCodePoint(vv)
                } else if (outEnd - outPos >= 2) {
                    val high = highSurrogate(vv).toChar()
                    val low = lowSurrogate(vv).toChar()

                    if (!predicate(high) || !predicate(low)) {
                        position(position() - 4)
                        return decodeUtf8Result(outPos - offset, -1)
                    }

                    out[outPos++] = high
                    out[outPos++] = low
                } else {
                    position(position() - 4)
                    return decodeUtf8Result(outPos - offset, 0)
                }
            }
            else -> unsupportedByteCount(v)
        }
    }

    return decodeUtf8Result(outPos - offset, 0)
}

private const val MaxCodePoint = 0X10ffff
private const val MinLowSurrogate = 0xdc00
private const val MinHighSurrogate = 0xd800
private const val MinSupplementary = 0x10000
private const val HighSurrogateMagic = MinHighSurrogate - (MinSupplementary ushr 10)

private fun isBmpCodePoint(cp: Int) = cp ushr 16 == 0
private fun isValidCodePoint(codePoint: Int) = codePoint <= MaxCodePoint
private fun lowSurrogate(cp: Int) = (cp and 0x3ff) + MinLowSurrogate
private fun highSurrogate(cp: Int) = (cp ushr 10) + HighSurrogateMagic

private fun indexOutOfBounds(offset: Int, length: Int, arrayLength: Int): Throwable =
    IndexOutOfBoundsException("$offset (offset) + $length (length) > $arrayLength (array.length)")

private fun malformedCodePoint(value: Int): Nothing =
    throw IllegalArgumentException("Malformed code-point ${Integer.toHexString(value)} found")

private fun unsupportedByteCount(b: Byte): Nothing =
    error("Unsupported byte code, first byte is 0x${(b.toInt() and 0xff).toString(16).padStart(2, '0')}")
