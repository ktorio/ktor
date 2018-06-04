package io.ktor.http

import io.ktor.util.*

fun URLBuilder.takeFrom(url: URLBuilder): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

fun URLBuilder.takeFrom(url: Url): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

val Url.fullPath: String
    get() {
        val parameters = when {
            parameters.isEmpty() && trailingQuery -> "?"
            !parameters.isEmpty() -> "?${decodeURLPart(parameters.formUrlEncode())}"
            else -> ""
        }
        val result = "$encodedPath$parameters".trim()
        return if (!result.startsWith("/")) "/$result" else result
    }

val Url.hostWithPort: String get() = "$host:$port"
