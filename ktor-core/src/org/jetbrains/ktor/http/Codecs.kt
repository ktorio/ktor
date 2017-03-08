package org.jetbrains.ktor.http

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

fun decodeURLQueryComponent(s: String): String = decodeScan(s, true, Charsets.UTF_8)
fun decodeURLPart(s: String): String = decodeScan(s, false, Charsets.UTF_8)

private fun decodeScan(s: String, plusIsSpace: Boolean, charset: Charset): String {
    for (index in 0..s.length - 1) {
        val ch = s[index]
        if (ch == '%' || (plusIsSpace && ch == '+')) {
            return decodeImpl(s, index, plusIsSpace, charset)
        }
    }

    return s
}

private fun decodeImpl(s: String, prefixLength: Int, plusIsSpace: Boolean, charset: Charset): String {
    val length = s.length
    // if length is big, it probably means it is encoded
    val sbSize = if (length > 255) length / 3 else length
    val sb = StringBuilder(sbSize)

    if (prefixLength > 0) {
        sb.append(s, 0, prefixLength)
    }

    var index = prefixLength

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < length) {
        val c = s[index]
        when {
            plusIsSpace && c == '+' -> {
                sb.append(' ')
                index++
            }
            c == '%' -> {
                // if ByteArray was not needed before, create it with an estimate of remaining string be all hex
                if (bytes == null)
                    bytes = ByteArray((length - index) / 3)

                // fill ByteArray with all the bytes, so Charset can decode text
                var count = 0
                while (index < length && s[index] == '%') {
                    if (index + 2 >= length) {
                        throw URISyntaxException(s, "Incomplete trailing HEX escape: ${s.substring(index)}", index)
                    }

                    val digit1 = charToHexDigit(s[index + 1])
                    val digit2 = charToHexDigit(s[index + 2])
                    if (digit1 == -1 || digit2 == -1) {
                        throw URISyntaxException(s, "Wrong HEX escape: %${s[index + 1]}${s[index + 2]}", index)
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
