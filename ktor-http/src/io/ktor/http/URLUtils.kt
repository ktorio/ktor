package io.ktor.http

import io.ktor.util.*
import java.net.*

fun URLBuilder.takeFrom(url: String) {
    takeFrom(URI(url))
}

fun URLBuilder.takeFrom(uri: URI) {
    uri.scheme?.let { protocol = URLProtocol.createOrDefault(it) }
    uri.host?.let { host = it }

    if (uri.userInfo != null && uri.userInfo.isNotEmpty()) {
        val parts = uri.userInfo.split(":")
        user = parts.first()
        password = parts.getOrNull(1)
    }

    port = if (uri.port > 0) {
        uri.port
    } else if (uri.scheme != null) {
        protocol.defaultPort
    } else {
        port
    }

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
val Url.hostWithPortIfRequired: String get() = if (port == protocol.defaultPort) host else hostWithPort
val Url.fullUrl: String get() {
    var full = "${protocol.name}://"
    if (user != null) {
        full += "$user"
        if (password != null) {
            full += ":$password"
        }
        full += "@"
    }
    full += "$hostWithPortIfRequired$fullPath"
    if (fragment.isNotEmpty()) full += "#$fragment"
    return full
}
