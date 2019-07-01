package io.ktor.utils.io.internal

import io.ktor.utils.io.charsets.*
import java.nio.*
import kotlin.math.*

internal fun ByteBuffer.decodeASCII(out: CharArray, offset: Int = 0, length: Int = out.size): Int {
    return if (hasArray()) {
        decodeASCII3_array(out, offset, length)
    } else decodeASCII3_buffer(out, offset, length)
}

internal fun ByteBuffer.decodeASCIILine(out: CharArray, offset: Int = 0, length: Int = out.size): Long {
    return when {
        hasArray() -> decodeASCIILine_array(out, offset, length)
        else -> decodeASCIILine_buffer(out, offset, length)
    }
}

private fun ByteBuffer.decodeASCIILine_array(out: CharArray, offset: Int, length: Int): Long {
    var cr = false

    val rc = decodeASCII3_array(out, offset, length) { ch ->
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
//            done by decodeASCII3_array
//            position(position() - 1) // push back a character after CR
            return decodeUtf8Result(decoded - 1, -1) // don't return CR
        }

        position(position() + 1) // consume LF
        if (decoded > 0 && out[decoded - 1] == '\r') {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR followed by LF
        }
    } else if (cr) { // trailing CR so we don't know if we have complete EOL yet
        val decoded = (rc shr 32).toInt()
        position(position() - 1)
        return decodeUtf8Result(decoded - 1, 2) // so we demand at least 2 bytes
    }

    return rc
}

private fun ByteBuffer.decodeASCIILine_buffer(out: CharArray, offset: Int, length: Int): Long {
    var cr = false

    val rc = decodeASCII3_buffer(out, offset, length) { ch ->
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
            position(position() - 1) // push back a character after CR
            return decodeUtf8Result(decoded - 1, -1) // don't return CR
        }

        position(position() + 1) // consume LF
        if (decoded > 0 && out[decoded - 1] == '\r') {
            return decodeUtf8Result(decoded - 1, -1) // don't return CR followed by LF
        }
    } else if (cr) { // trailing CR so we don't know if we have complete EOL yet
        val decoded = (rc shr 32).toInt()
        position(position() - 1)
        return decodeUtf8Result(decoded - 1, 2) // so we demand at least 2 bytes
    }

    return rc
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
): Long {
    var pos = offset
    val end = offset + length
    val array = array()!!
    var srcPos = arrayOffset() + position()
    val srcEnd = srcPos + remaining()

    if (end <= out.size && srcEnd <= array.size) {
        while (srcPos < srcEnd) {
            val b = array[srcPos]
            if (b < 0) break

            val ch = b.toChar()
            if (!predicate(ch)) {
                position(srcPos - arrayOffset())
                return decodeUtf8Result(pos - offset, -1)
            }
            if (pos >= end) {
                break
            }

            out[pos] = ch

            pos++
            srcPos++
        }

        position(srcPos - arrayOffset())
    }

    return decodeUtf8Result(pos - offset, 0)
}

private inline fun ByteBuffer.decodeASCII3_buffer(
    out: CharArray,
    offset: Int,
    length: Int,
    predicate: (Char) -> Boolean
): Long {
    var pos = offset
    val end = offset + length

    var pushBack = false
    var predicateFailed = false

    if (end <= out.size) {
        while (hasRemaining()) {
            val b = get()
            if (b < 0) {
                pushBack = true
                break
            }

            val ch = b.toChar()
            if (!predicate(ch)) {
                pushBack = true
                predicateFailed = true
                break
            }

            if (pos >= end) {
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

    return decodeUtf8Result(pos - offset, if (predicateFailed) -1 else 0)
}

