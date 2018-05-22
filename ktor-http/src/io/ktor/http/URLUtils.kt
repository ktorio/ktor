package io.ktor.http

import io.ktor.util.*
import java.net.*

fun URLBuilder.takeFrom(url: String) {
    takeFrom(URI(url))
}

fun URLBuilder.takeFrom(uri: URI) {
    if (uri.port > 0) {
        port = uri.port
    } else {
        when (uri.scheme) {
            "http" -> port = 80
            "https" -> port = 443
        }
    }

    uri.scheme?.let { protocol = URLProtocol.createOrDefault(it) }
    uri.host?.let { host = it }
    uri.rawPath?.let {
        encodedPath = when (it) {
            "" -> "/"
            else -> it
        }
    }
    uri.query?.let { parameters.appendAll(parseQueryString(it)) }
    if (uri.query?.isEmpty() == true) {
        trailingQuery = true
    }

    uri.fragment?.let { fragment = it }
}

fun URLBuilder.takeFrom(url: java.net.URL) = takeFrom(url.toURI())

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
