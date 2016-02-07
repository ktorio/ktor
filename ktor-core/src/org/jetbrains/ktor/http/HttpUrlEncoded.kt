package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.net.*
import java.nio.charset.*

public fun ApplicationRequest.parseUrlEncodedParameters(): ValuesMap {
    return content.get<String>().parseUrlEncodedParameters(contentCharset() ?: Charsets.UTF_8)
}

public fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8): ValuesMap {
    val parameters = split("&").map { it.substringBefore("=") to it.substringAfter("=", "") }
    val encoding = parameters.firstOrNull { it.first == "_charset_" }?.second ?: defaultEncoding.name()

    return parameters.fold(ValuesMap.Builder()) { builder, pair ->
        builder.append(URLDecoder.decode(pair.first, encoding), URLDecoder.decode(pair.second, encoding))
        builder
    }.build()
}

public fun List<Pair<String, String?>>.formUrlEncode(): String {
    return StringBuilder().apply {
        formUrlEncodeTo(this)
    }.toString()
}

public fun List<Pair<String, String?>>.formUrlEncodeTo(out: Appendable) {
    filter { it.second != null }.joinTo(out, "&") { "${encodeURLQueryComponent(it.first)}=${encodeURLQueryComponent(it.second.toString())}" }
}

public fun ValuesMap.formUrlEncode(): String {
    return entries()
            .flatMap { e -> e.value.map { e.key to it } }
            .formUrlEncode()
}

public fun ValuesMap.formUrlEncodeTo(out: Appendable) {
    entries()
            .flatMap { e -> e.value.map { e.key to it } }
            .formUrlEncodeTo(out)
}
