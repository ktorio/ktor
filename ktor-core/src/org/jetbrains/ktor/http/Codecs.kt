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

fun decodeURLQueryComponent(s: String): String = decode(s, true, Charsets.UTF_8)
fun decodeURLPart(s: String): String = decode(s, false, Charsets.UTF_8)

/**
 * Optimized version of [URLDecoder.decode]
 */
private fun decode(s: String, plusIsSpace: Boolean, charset: Charset): String {
    // cache length on stack
    val length = s.length

    // do not create StringBuilder until we need changing something in the string
    var sb: StringBuilder? = null
    // if length is big, it probably means it is encoded
    val sbSize = if (length > 255) length / 3 else length

    var index = 0

    // reuse ByteArray for hex decoding stripes
    var bytes: ByteArray? = null

    while (index < length) {
        val c = s[index]
        when {
            plusIsSpace && c == '+' -> {
                // if StringBuilder was not needed before, create it and append all the string before
                if (sb == null) {
                    sb = StringBuilder(sbSize)
                    sb.append(s, 0, index)
                }
                sb.append(' ')
                index++
            }
            c == '%' -> {
                // if ByteArray was not needed before, create it with an estimate of remaining string be all hex
                if (bytes == null)
                    bytes = ByteArray((length - index) / 3)

                // if StringBuilder was not needed before, create it and append all the string before
                // Note: we assume that broken URLs ending with incomplete %xx are rare
                //       so we don't try to save on StringBuilder at the cost of extra control statements
                if (sb == null) {
                    sb = StringBuilder(sbSize)
                    sb.append(s, 0, index)
                }

                // fill ByteArray with all the bytes, so Charset can decode text
                var count = 0
                while (index + 2 < length && s[index] == '%') {
                    val digit1 = charToHexDigit(s[index + 1])
                    val digit2 = charToHexDigit(s[index + 2])
                    if (digit1 == -1 || digit2 == -1)
                        break

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(java.lang.String(bytes, 0, count, charset))
            }
            else -> {
                // Append text if we already has a difference with the original string
                if (sb != null)
                    sb.append(c)
                index++
            }
        }
    }

    return if (sb != null) sb.toString() else s
}

private fun charToHexDigit(c2: Char) = when (c2) {
    in '0'..'9' -> c2 - '0'
    in 'A'..'F' -> c2 - 'A' + 10
    in 'a'..'f' -> c2 - 'a' + 10
    else -> -1
}
