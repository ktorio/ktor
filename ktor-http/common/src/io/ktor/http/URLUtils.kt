/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Construct [Url] from [urlString].
 */
@Suppress("FunctionName")
public fun Url(urlString: String): Url = URLBuilder(urlString).build()

/**
 * Construct [Url] from [builder] without building origin.
 */
@Suppress("FunctionName")
public fun Url(builder: URLBuilder): Url = URLBuilder().takeFrom(builder).build()

/**
 * Construct [URLBuilder] from [urlString].
 */
@Suppress("FunctionName")
public fun URLBuilder(urlString: String): URLBuilder = URLBuilder().takeFrom(urlString)

/**
 * Construct [URLBuilder] from [url].
 */
@Suppress("FunctionName")
public fun URLBuilder(url: Url): URLBuilder = URLBuilder().takeFrom(url)

/**
 * Construct [URLBuilder] from [builder].
 */
@Suppress("FunctionName")
public fun URLBuilder(builder: URLBuilder): URLBuilder = URLBuilder().takeFrom(builder)

/**
 * Take components from another [url] builder
 */
public fun URLBuilder.takeFrom(url: URLBuilder): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPathSegments = url.encodedPathSegments
    encodedUser = url.encodedUser
    encodedPassword = url.encodedPassword
    encodedParameters = url.encodedParameters
    encodedFragment = url.encodedFragment
    trailingQuery = url.trailingQuery

    return this
}

/**
 * Take components from another [url]
 */
public fun URLBuilder.takeFrom(url: Url): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.port
    encodedPath = url.encodedPath
    encodedUser = url.encodedUser
    encodedPassword = url.encodedPassword
    encodedParameters = ParametersBuilder().apply { appendAll(parseQueryString(url.encodedQuery, decode = false)) }
    encodedFragment = url.encodedFragment
    trailingQuery = url.trailingQuery

    return this
}

/**
 * Full encoded path with query string but without domain, port and schema
 */
public val Url.fullPath: String
    get() = buildString { appendUrlFullPath(encodedPath, encodedQuery, trailingQuery) }

/**
 * Host:port pair, not normalized so port is always specified even if the port is schema's default
 */
public val Url.hostWithPort: String get() = "$host:$port"

internal fun Appendable.appendUrlFullPath(
    encodedPath: String,
    encodedQuery: String,
    trailingQuery: Boolean
) {
    if (encodedPath.isNotBlank() && !encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (encodedQuery.isNotEmpty() || trailingQuery) {
        append("?")
    }

    append(encodedQuery)
}

public fun Appendable.appendUrlFullPath(
    encodedPath: String,
    encodedQueryParameters: ParametersBuilder,
    trailingQuery: Boolean
) {
    if (encodedPath.isNotBlank() && !encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!encodedQueryParameters.isEmpty() || trailingQuery) {
        append("?")
    }

    encodedQueryParameters.entries()
        .flatMap { (key, value) ->
            if (value.isEmpty()) listOf(key to null) else value.map { key to it }
        }
        .joinTo(this, "&") {
            val key = it.first
            if (it.second == null) {
                key
            } else {
                val value = it.second.toString()
                "$key=$value"
            }
        }
}

internal fun StringBuilder.appendUserAndPassword(encodedUser: String?, encodedPassword: String?) {
    if (encodedUser == null) {
        return
    }
    append(encodedUser)

    if (encodedPassword != null) {
        append(':')
        append(encodedPassword)
    }

    append("@")
}
