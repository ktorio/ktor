/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

/**
 * Construct [Url] from [urlString].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.Url)
 */
@Suppress("FunctionName")
public fun Url(urlString: String): Url = URLBuilder(urlString).build()

/**
 * Construct [Url] from [builder] without building origin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.Url)
 */
@Suppress("FunctionName")
public fun Url(builder: URLBuilder): Url = URLBuilder().takeFrom(builder).build()

/**
 * Construct a [Url] by applying [block] an empty [UrlBuilder].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.buildUrl)
 */
public fun buildUrl(block: URLBuilder.() -> Unit): Url = URLBuilder().apply(block).build()

/**
 * Parses the given URL string and returns a [Url] object if valid, otherwise, it returns `null`.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.parseUrl)
 *
 * @param urlString The URL string to be parsed.
 * @return A [Url] object if the URL is valid or `null` otherwise.
 */
public fun parseUrl(urlString: String): Url? {
    return try {
        URLBuilder(urlString).takeIf { it.host.isNotEmpty() }?.build()
    } catch (cause: URLParserException) {
        null
    }
}

/**
 * Construct [URLBuilder] from [urlString].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLBuilder)
 */
@Suppress("FunctionName")
public fun URLBuilder(urlString: String): URLBuilder = URLBuilder().takeFrom(urlString)

/**
 * Construct [URLBuilder] from [url].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLBuilder)
 */
@Suppress("FunctionName")
public fun URLBuilder(url: Url): URLBuilder = URLBuilder().takeFrom(url)

/**
 * Construct [URLBuilder] from [builder].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLBuilder)
 */
@Suppress("FunctionName")
public fun URLBuilder(builder: URLBuilder): URLBuilder = URLBuilder().takeFrom(builder)

/**
 * Take components from another [url] builder
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.takeFrom)
 */
public fun URLBuilder.takeFrom(url: URLBuilder): URLBuilder {
    protocolOrNull = url.protocolOrNull
    host = url.host
    port = url.port
    encodedPathSegments = url.encodedPathSegments
    encodedUser = url.encodedUser
    encodedPassword = url.encodedPassword
    encodedParameters = ParametersBuilder().apply { appendAll(url.encodedParameters) }
    encodedFragment = url.encodedFragment
    trailingQuery = url.trailingQuery

    return this
}

/**
 * Take components from another [url]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.takeFrom)
 */
public fun URLBuilder.takeFrom(url: Url): URLBuilder {
    protocolOrNull = url.protocolOrNull
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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.fullPath)
 */
public val Url.fullPath: String
    get() = buildString { appendUrlFullPath(encodedPath, encodedQuery, trailingQuery) }

/**
 * Host:port pair, not normalized so port is always specified even if the port is schema's default
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.hostWithPort)
 */
public val Url.hostWithPort: String get() = "$host:$port"

/**
 * Returns "host:port" when port is specified. Else, returns host.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.hostWithPortIfSpecified)
 */
public val Url.hostWithPortIfSpecified: String get() =
    when (specifiedPort) {
        DEFAULT_PORT, protocol.defaultPort -> host
        else -> hostWithPort
    }

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

/**
 * Checks if [Url] has absolute path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isAbsolutePath)
 */
public val Url.isAbsolutePath: Boolean get() = rawSegments.firstOrNull() == ""

/**
 * Checks if [Url] has absolute path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isRelativePath)
 */
public val Url.isRelativePath: Boolean get() = !isAbsolutePath

/**
 * Checks if [Url] has absolute path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isAbsolutePath)
 */
public val URLBuilder.isAbsolutePath: Boolean get() = pathSegments.firstOrNull() == ""

/**
 * Checks if [Url] has absolute path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.isRelativePath)
 */
public val URLBuilder.isRelativePath: Boolean get() = !isAbsolutePath

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
