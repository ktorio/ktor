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

private val hexLow = "0123456789abcdef"
private val hexHigh = "0123456789ABCDEF"

/**
 * Optimized version of [URLDecoder.decode]
 */
private fun decode(s: String, plusIsSpace: Boolean, enc: Charset): String {
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
                    val c1 = s[index + 1]
                    val c2 = s[index + 2]
                    val digit1 = hexLow.indexOf(c1).let { if (it == -1) hexHigh.indexOf(c1) else it }
                    val digit2 = hexLow.indexOf(c2).let { if (it == -1) hexHigh.indexOf(c2) else it }
                    if (digit1 == -1 || digit2 == -1)
                        throw IllegalArgumentException("Escaped text `$c1$c2` is not a hex byte representation")

                    bytes[count++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                }

                // Decode chars from bytes and put into StringBuilder
                // Note: Tried using ByteBuffer and using enc.decode() – it's slower
                sb.append(java.lang.String(bytes, 0, count, enc))
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
