package org.jetbrains.ktor.http

import java.net.*
import java.nio.charset.*

fun encodeURLQueryComponent(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
fun encodeURLPart(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20").replace("%2b", "+").replace("%2B", "+").replace("*", "%2A").replace("%7E", "~")

fun decodeURLQueryComponent(s: String): String = decode(s, true, Charsets.UTF_8)
fun decodeURLPart(s: String): String = decode(s, false, Charsets.UTF_8)

private val hexLow = "0123456789abcdef"
private val hexHigh = "0123456789ABCDEF"

/*
 * Optimized copy of URLDecoder.decode
 * @author  Mark Chamness
 * @author  Michael McCloskey
 * @author  Ilya Ryzhenkov
 */
private fun decode(s: String, plusIsSpace: Boolean, enc: Charset): String {
    val length = s.length
    var sb: StringBuilder? = null
    val sbSize = if (length > 500) length / 3 else length
    var index = 0
    var c: Char
    var bytes: ByteArray? = null

    while (index < length) {
        c = s[index]
        when (c) {
            '+' -> {
                if (plusIsSpace) {
                    if (sb == null) {
                        sb = StringBuilder(sbSize)
                        sb.append(s, 0, index)
                    }
                    sb.append(' ')
                } else {
                    if (sb != null)
                        sb.append('+')
                }
                index++
            }
            '%' -> {
                if (bytes == null)
                    bytes = ByteArray((length - index) / 3)
                var pos = 0

                if (sb == null) {
                    sb = StringBuilder(sbSize)
                    sb.append(s, 0, index)
                }

                while (index + 2 < length && c == '%') {
                    val c1 = s[index + 1]
                    val c2 = s[index + 2]
                    val digit1 = hexLow.indexOf(c1).let { if (it == -1) hexHigh.indexOf(c1) else it }
                    val digit2 = hexLow.indexOf(c2).let { if (it == -1) hexHigh.indexOf(c2) else it }
                    if (digit1 == -1 || digit2 == -1)
                        throw IllegalArgumentException("Escaped text `$c1$c2` is not a hex byte representation")

                    bytes[pos++] = (digit1 * 16 + digit2).toByte()
                    index += 3
                    if (index < length)
                        c = s[index]
                }

                if (index < length && c == '%')
                    throw IllegalArgumentException("Incomplete trailing escape (%) pattern")

                sb.append(java.lang.String(bytes, 0, pos, enc))
            }
            else -> {
                if (sb != null)
                    sb.append(c)
                index++
            }
        }
    }

    return if (sb != null) sb.toString() else s
}
