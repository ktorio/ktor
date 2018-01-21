package io.ktor.http

import io.ktor.util.*
import java.net.*
import java.nio.charset.*

fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8, limit: Int = 1000): StringValues {
    val parameters = split("&", limit = limit).map { it.substringBefore("=") to it.substringAfter("=", "") }
    val encoding = parameters.firstOrNull { it.first == "_charset_" }?.second ?: defaultEncoding.name()

    return StringValues.build {
        parameters.forEach { append(URLDecoder.decode(it.first, encoding), URLDecoder.decode(it.second, encoding)) }
    }
}

fun List<Pair<String, String?>>.formUrlEncode(): String {
    return StringBuilder().apply {
        formUrlEncodeTo(this)
    }.toString()
}

fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable) {
    filter { it.second != null }.joinTo(out, "&") { "${encodeURLQueryComponent(it.first)}=${encodeURLQueryComponent(it.second.toString())}" }
}

fun StringValues.formUrlEncode(): String {
    return entries()
            .flatMap { e -> e.value.map { e.key to it } }
            .formUrlEncode()
}

fun StringValues.formUrlEncodeTo(out: Appendable) {
    entries()
            .flatMap { e -> e.value.map { e.key to it } }
            .formUrlEncodeTo(out)
}
