package io.ktor.http

import io.ktor.util.*
import java.net.*


fun URLBuilder.takeFrom(uri: URI) {
    port = uri.port.takeIf { it > 0 } ?: if (uri.scheme == "https") 443 else 80
    protocol = URLProtocol.createOrDefault(uri.scheme)
    host = uri.host
    val path = uri.path
    encodedPath = when (path) {
        null -> "/"
        "" -> "/"
        else -> path
    }
    uri.query?.let { parameters.appendAll(parseQueryString(it)) }
    if (uri.query?.isEmpty() == true) {
        trailingQuery = true
    }

    fragment = uri.fragment ?: ""
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

val Url.fullPath: String
    get() {
        val parameters = when {
            parameters.isEmpty() && trailingQuery -> "?"
            !parameters.isEmpty() -> "?${decodeURLPart(parameters.formUrlEncode())}"
            else -> ""
        }
        val result = "$encodedPath$parameters"
        return if (result.isEmpty()) "/" else result
    }

val Url.hostWithPort: String get() = "$host:$port"
