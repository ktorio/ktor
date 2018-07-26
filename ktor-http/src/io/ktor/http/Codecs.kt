package io.ktor.http

import kotlinx.io.charsets.*
import kotlinx.io.core.*

private val URL_ALPHABET = (('a'..'z') + ('A'..'Z') + ('0'..'9')).map { it.toByte() }

/**
 * https://tools.ietf.org/html/rfc3986#section-2
 */
private val URL_PROTOCOL_PART = listOf(
    ':', '/', '?', '#', '[', ']', '@',  // general
    '!', '$', '&', '\'', '(', ')', '*', ',', ';', '=',  // sub-components
    '-', '.', '_', '~', '+' // unreserved
).map { it.toByte() }

/**
 * Oauth specific percent encoding
 * https://tools.ietf.org/html/rfc5849#section-3.6
 */
private val OAUTH_SYMBOLS = listOf('-', '.', '_', '~').map { it.toByte() }

/**
 * Encode url part as specified in
 * https://tools.ietf.org/html/rfc3986#section-2
 */
fun String.encodeURLQueryComponent(
    encodeFull: Boolean = false,
    spaceToPlus: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = buildString {
    val content = charset.newEncoder().encode(this@encodeURLQueryComponent)
    content.forEach {
        when {
            it == ' '.toByte() -> if (spaceToPlus) append('+') else append("%20")
            it in URL_ALPHABET || (!encodeFull && it in URL_PROTOCOL_PART) -> append(it.toChar())
            else -> append(it.percentEncode())
        }
    }
}

/**
 * Encode [this] in percent encoding specified here:
 * https://tools.ietf.org/html/rfc5849#section-3.6
 */
fun String.encodeOAuth(): String = encodeURLParameter()

/**
 * Encode [this] as query parameter
 */
fun String.encodeURLParameter(
    spaceToPlus: Boolean = false
): String = buildString {
    val content = Charsets.UTF_8.newEncoder().encode(this@encodeURLParameter)
    content.forEach {
        when {
            it in URL_ALPHABET || it in OAUTH_SYMBOLS -> append(it.toChar())
            spaceToPlus && it == ' '.toByte() -> append('+')
            else -> append(it.percentEncode())
        }
    }
}

fun CharSequence.decodeURLQueryComponent(
    start: Int = 0, end: Int = length,
    plusIsSpace: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(start, end, plusIsSpace, charset)

fun String.decodeURLPart(
    start: Int = 0, end: Int = length,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(start, end, false, charset)

private fun CharSequence.decodeScan(start: Int, end: Int, plusIsSpace: Boolean, charset: Charset): String {
    for (index in start until end) {
        val ch = this[index]
        if (ch == '%' || (plusIsSpace && ch == '+')) {
            return decodeImpl(start, end, index, plusIsSpace, charset)
        }
    }
    return if (start == 0 && end == length) toString() else substring(start, end)
}

private fun CharSequence.decodeImpl(
    start: Int,
    end: Int,
    prefixEnd: Int,
    plusIsSpace: Boolean,
    charset: Charset
): String {
    val length = end - start
    // if length is big, it probably means it is encoded
    val sbSize = if (length > 255) length / 3 else length
    val sb = StringBuilder(sbSize)

    if (prefixEnd > start) {
        sb.append(this, start, prefixEnd)
    }

    var index = prefixEnd

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < end) {
        val c = this[index]
        when {
            plusIsSpace && c == '+' -> {
                sb.append(' ')
                index++
            }
            c == '%' -> {
                // if ByteArray was not needed before, create it with an estimate of remaining string be all hex
                if (bytes == null)
                    bytes = ByteArray((end - index) / 3)

                // fill ByteArray with all the bytes, so Charset can decode text
                var count = 0
                while (index < end && this[index] == '%') {
                    if (index + 2 >= end) {
                        throw URLDecodeException(
                            "Incomplete trailing HEX escape: ${substring(index)}, in $this at $index"
                        )
                    }

                    val digit1 = charToHexDigit(this[index + 1])
                    val digit2 = charToHexDigit(this[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw URLDecodeException(
                            "Wrong HEX escape: %${this[index + 1]}${this[index + 2]}, in $this, at $index"
                        )
                    }

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(String(bytes, offset = 0, length = count, charset = charset))
            }
            else -> {
                sb.append(c)
                index++
            }
        }
    }

    return sb.toString()
}

class URLDecodeException(message: String) : Exception(message)

private fun Byte.percentEncode(): String {
    val code = (toInt() and 0xff).toString(radix = 16).toUpperCase()
    return "%${code.padStart(length = 2, padChar = '0')}"
}

private fun charToHexDigit(c2: Char) = when (c2) {
    in '0'..'9' -> c2 - '0'
    in 'A'..'F' -> c2 - 'A' + 10
    in 'a'..'f' -> c2 - 'a' + 10
    else -> -1
}

private fun ByteReadPacket.forEach(block: (Byte) -> Unit) {
    takeWhile { buffer ->
        while (buffer.canRead()) {
            block(buffer.readByte())
        }
        true
    }
}
