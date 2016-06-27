package org.jetbrains.ktor.http

import java.net.*

fun encodeURLQueryComponent(s: String): String = URLEncoder.encode(s, Charsets.UTF_8.name())
fun encodeURLPart(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20").replace("%2b", "+").replace("%2B", "+").replace("*", "%2A").replace("%7E", "~")

fun decodeURLQueryComponent(s: String): String = URLDecoder.decode(s, Charsets.UTF_8.name())
fun decodeURLPart(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), Charsets.UTF_8.name())


