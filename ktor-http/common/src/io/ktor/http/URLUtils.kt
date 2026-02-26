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
    } catch (_: Exception) {
        null
    }
}

/**
 * Construct [URLBuilder] from [urlString].
 *
 * Unlike [takeFrom], which resolves the given string as a relative URL against an existing builder state,
 * this function treats [urlString] as a standalone URL. When no scheme is present and the string does not
 * start with `/`, the string is interpreted as an authority (host with optional port and path) rather than
 * a relative path.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.URLBuilder)
 */
@Suppress("FunctionName")
public fun URLBuilder(urlString: String): URLBuilder {
    val trimmed = urlString.trim()
    if (trimmed.isEmpty()) return URLBuilder()

    // Strings that start with '/' are absolute paths or authority references (like "//host")
    if (trimmed.startsWith('/')) {
        return URLBuilder().takeFrom(trimmed)
    }

    // Strings that contain "://" have an explicit scheme
    if (trimmed.contains("://")) {
        return URLBuilder().takeFrom(trimmed)
    }

    // Check for special schemes that don't use "://" (e.g., "mailto:user@host", "data:...", "tel:...")
    val colonIndex = trimmed.indexOf(':')
    if (colonIndex > 0) {
        val potentialScheme = trimmed.substring(0, colonIndex).lowercase()
        if (potentialScheme in SPECIAL_SCHEMES_WITHOUT_AUTHORITY) {
            return URLBuilder().takeFrom(trimmed)
        }

        // If what follows the colon up to the next delimiter is purely numeric, treat as host:port
        val afterColon = trimmed.substring(colonIndex + 1).takeWhile { it != '/' && it != '?' && it != '#' }
        if (afterColon.isEmpty() || !afterColon.all { it.isDigit() }) {
            // Non-numeric after colon — likely a scheme:payload pattern, pass through as-is
            return URLBuilder().takeFrom(trimmed)
        }
    }

    // No scheme detected — treat the string as an authority (host[:port][/path][?query][#fragment])
    return URLBuilder().takeFrom("//$trimmed")
}

private val SPECIAL_SCHEMES_WITHOUT_AUTHORITY = setOf("mailto", "data", "tel", "about")

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
