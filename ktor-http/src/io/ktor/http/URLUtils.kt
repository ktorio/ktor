package io.ktor.http

import io.ktor.util.*

/**
 * Construct [Url] from [urlString]
 */
fun Url(urlString: String): Url = URLBuilder(urlString).build()

/**
 * Construct [UrlBuilder] from [urlString]
 */
fun URLBuilder(urlString: String): URLBuilder = URLBuilder().takeFrom(urlString)

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
    get() = buildString { appendUrlFullPath(encodedPath, parameters, trailingQuery) }

val Url.hostWithPort: String get() = "$host:$port"

internal fun Appendable.appendUrlFullPath(
    encodedPath: String,
    queryParameters: Parameters,
    trailingQuery: Boolean
) {
    if (!encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!queryParameters.isEmpty() || trailingQuery) {
        append("?")
    }

    queryParameters.formUrlEncodeTo(this)
}
