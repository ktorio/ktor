package io.ktor.utils.io.charsets

import java.nio.*

internal inline fun ByteBuffer.decodeASCII(
    out: CharArray,
    offset: Int = 0,
    length: Int = out.size,
    predicate: (Char) -> Boolean
): Int {
    return if (hasArray()) {
        decodeASCII3_array(out, offset, length, predicate)
    } else decodeASCII3_buffer(out, offset, length, predicate)
}

internal fun ByteBuffer.decodeASCII(out: CharArray, offset: Int = 0, length: Int = out.size): Int {
    return if (hasArray()) {
        decodeASCII3_array(out, offset, length)
    } else decodeASCII3_buffer(out, offset, length)
}

private fun ByteBuffer.decodeASCII3_array(out: CharArray, offset: Int, length: Int): Int {
    var pos = offset
    val end = offset + length
    val array = array()!!
    var srcPos = arrayOffset() + position()
    val srcEnd = srcPos + remaining()

    if (end <= out.size && srcEnd <= array.size) {
        while (srcPos < srcEnd && pos < end) {
            val b = array[srcPos]
            if (b < 0) break

            out[pos] = b.toChar()

            pos++
            srcPos++
        }

        position(srcPos - arrayOffset())
    }

    return pos - offset
}

private fun ByteBuffer.decodeASCII3_buffer(out: CharArray, offset: Int, length: Int): Int {
    var pos = offset
    val end = offset + length

    var pushBack = false

    if (end <= out.size) {
        while (hasRemaining()) {
            val b = get()
            if (b < 0) {
                pushBack = true
                break
            }
            if (pos >= end) {
                pushBack = true
                break
            }
            out[pos] = b.toChar()
            pos++
        }
    }

    if (pushBack) {
        position(position() - 1)
    }

    return pos - offset
}

private inline fun ByteBuffer.decodeASCII3_array(
    out: CharArray,
    offset: Int,
    length: Int,
    predicate: (Char) -> Boolean
): Int {
    var pos = offset
    val end = offset + length
    val array = array()!!
    var srcPos = arrayOffset() + position()
    val srcEnd = srcPos + remaining()

    if (end <= out.size && srcEnd <= array.size) {
        while (srcPos < srcEnd && pos < end) {
            val b = array[srcPos]
            if (b < 0) break

            val ch = b.toChar()
            if (!predicate(ch)) {
                srcPos--
                break
            }
            out[pos] = ch

            pos++
            srcPos++
        }

        position(srcPos - arrayOffset())
    }

    return pos - offset
}

private inline fun ByteBuffer.decodeASCII3_buffer(
    out: CharArray,
    offset: Int,
    length: Int,
    predicate: (Char) -> Boolean
): Int {
    var pos = offset
    val end = offset + length

    var pushBack = false

    if (end <= out.size) {
        while (hasRemaining()) {
            val b = get()
            if (b < 0) {
                pushBack = true
                break
            }
            if (pos >= end) {
                pushBack = true
                break
            }
            val ch = b.toChar()
            if (!predicate(ch)) {
                pushBack = true
                break
            }

            out[pos] = ch
            pos++
        }
    }

    if (pushBack) {
        position(position() - 1)
    }

    return pos - offset
}


