package org.jetbrains.ktor.http

import java.net.*

@Deprecated("This is deprecated as it not clear which part of the query we are decoding and how does it handle space and plus characters", ReplaceWith("decodeURLQueryComponent(this)"))
fun String.decodeURL(): String = decodeURLQueryComponent(this)
@Deprecated("This is deprecated as it not clear which part of the query we are encoding and how does it handle space and plus characters", ReplaceWith("encodeURLQueryComponent(this)"))
fun String.encodeURL(): String = encodeURLQueryComponent(this)

fun encodeURLQueryComponent(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())
fun encodeURLPart(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20").replace("%2b", "+").replace("%2B", "+")

fun decodeURLQueryComponent(s: String) = URLDecoder.decode(s, Charsets.UTF_8.name())
fun decodeURLPart(s: String) = URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8.name())


