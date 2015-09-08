package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.net.*
import java.nio.charset.*

public fun ApplicationRequest.parseUrlEncodedParameters(): ValuesMap =
    content.get<String>().parseUrlEncodedParameters(contentCharset ?: Charsets.UTF_8)

public fun String.parseUrlEncodedParameters(defaultEncoding: Charset = Charsets.UTF_8): ValuesMap {
    val parameters = split("&").map { it.substringBefore("=") to it.substringAfter("=", "") }
    val encoding = parameters.firstOrNull { it.first == "_charset_" }?.second ?: defaultEncoding.name()

    return parameters.fold(ValuesMap.Builder()) { builder, pair ->
        builder.append(URLDecoder.decode(pair.first, encoding), URLDecoder.decode(pair.second, encoding))
        builder
    }.build()
}

public fun List<Pair<String, *>>.formUrlEncode() =
        StringBuilder {
            formUrlEncodeTo(this)
        }.toString()

public fun List<Pair<String, *>>.formUrlEncodeTo(out: Appendable) {
    joinTo(out, "&") { "${it.first.encodeURL()}=${it.second.toString().encodeURL()}" }
}

public fun ValuesMap.formUrlEncode() =
        entries().flatMap { e -> e.value.map { e.key to e.value } }
        .formUrlEncode()

public fun ValuesMap.formUrlEncodeTo(out: Appendable) {
    entries().flatMap { e -> e.value.map { e.key to e.value } }
            .formUrlEncodeTo(out)
}
