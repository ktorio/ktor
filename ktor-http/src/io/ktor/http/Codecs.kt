package io.ktor.http

import java.net.*
import java.nio.charset.*

fun encodeURLQueryComponent(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
fun encodeURLPart(s: String): String {
    val encoded = URLEncoder.encode(s, Charsets.UTF_8.name())
    return encoded
            .replace("+", "%20")
            .replace("%2b", "+")
            .replace("%2B", "+")
            .replace("*", "%2A")
            .replace("%7E", "~")
}

fun decodeURLQueryComponent(text: CharSequence, start: Int = 0, end: Int = text.length): String {
    return decodeScan(text, start, end, true, Charsets.UTF_8)
}

fun decodeURLPart(text: String, start: Int = 0, end: Int = text.length): String {
    return decodeScan(text, start, end, false, Charsets.UTF_8)
}

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

private fun decodeImpl(text: CharSequence, start: Int, end: Int, prefixEnd: Int, plusIsSpace: Boolean, charset: Charset): String {
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
                        throw URISyntaxException(text.toString(), "Incomplete trailing HEX escape: ${text.substring(index)}", index)
                    }

                    val digit1 = charToHexDigit(text[index + 1])
                    val digit2 = charToHexDigit(text[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw URISyntaxException(text.toString(), "Wrong HEX escape: %${text[index + 1]}${text[index + 2]}", index)
                    }

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(java.lang.String(bytes, 0, count, charset))
            }
            else -> {
                sb.append(c)
                index++
            }
        }
    }

    return sb.toString()
}

private fun charToHexDigit(c2: Char) = when (c2) {
    in '0'..'9' -> c2 - '0'
    in 'A'..'F' -> c2 - 'A' + 10
    in 'a'..'f' -> c2 - 'a' + 10
    else -> -1
}
