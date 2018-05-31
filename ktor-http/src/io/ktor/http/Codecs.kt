package io.ktor.http

import kotlinx.io.charsets.*
import kotlinx.io.core.*

/**
https://tools.ietf.org/html/rfc3986#section-2
 */
private val URL_PROTOCOL_PART = listOf(
    ':', '/', '?', '#', '[', ']', '@',  // general
    '!', '$', '&', '\'', '(', ')', '*', ',', ';', '=',  // sub-components
    '-', '.', '_', '~', '+' // unreserved
).map { it.toByte() }

private val URL_ALPHABET = (('a'..'z') + ('A'..'Z') + ('0'..'9')).map { it.toByte() }
/**
 * Encode url part as specified in
 * https://tools.ietf.org/html/rfc3986#section-2
 */
fun encodeURLQueryComponent(
    part: String,
    encodeFull: Boolean = false,
    spaceToPlus: Boolean = false,
    charset: Charset = Charsets.UTF_8
): String = buildString {
    val content = charset.newEncoder().encode(part)
    content.forEach {
        when {
            it == ' '.toByte() -> if (spaceToPlus) append('+') else append("%20")
            it in URL_ALPHABET || (!encodeFull && it in URL_PROTOCOL_PART) -> append(it.toChar())
            else -> append(it.percentEncode())
        }
    }
}


/**
 * Encode url-part string. Using url-parts, doesn't change url structure
 */
fun encodeURLPart(part: String): String = encodeURLQueryComponent(part, encodeFull = true)

fun decodeURLQueryComponent(
    text: CharSequence,
    start: Int = 0, end: Int = text.length,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(text, start, end, false, charset)

fun decodeURLPart(
    text: String,
    start: Int = 0, end: Int = text.length,
    charset: Charset = Charsets.UTF_8
): String = decodeScan(text, start, end, false, charset)

private fun decodeScan(text: CharSequence, start: Int, end: Int, plusIsSpace: Boolean, charset: Charset): String {
    for (index in start until end) {
        val ch = text[index]
        if (ch == '%' || (plusIsSpace && ch == '+')) {
            return decodeImpl(text, start, end, index, plusIsSpace, charset)
        }
    }
    if (start == 0 && end == text.length)
        return text.toString()
    return text.substring(start, end)
}

private fun decodeImpl(
    text: CharSequence,
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
        sb.append(text, start, prefixEnd)
    }

    var index = prefixEnd

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < end) {
        val c = text[index]
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
                while (index < end && text[index] == '%') {
                    if (index + 2 >= end) {
                        throw URLDecodeException(
                            "Incomplete trailing HEX escape: ${text.substring(index)}, in $text at $index"
                        )
                    }

                    val digit1 = charToHexDigit(text[index + 1])
                    val digit2 = charToHexDigit(text[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw URLDecodeException(
                            "Wrong HEX escape: %${text[index + 1]}${text[index + 2]}, in $text, at $index"
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
