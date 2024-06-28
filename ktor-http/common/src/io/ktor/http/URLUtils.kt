/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

/**
 * Construct [Url] from [urlString].
 */
@Suppress("FunctionName")
public fun Url(urlString: String, relative: Boolean = false): Url =
    UriParser.parse(urlString, relative).toUrl()

/**
 * Construct [Url] from [builder] without building origin. TODO wut?
 */
@Suppress("FunctionName")
public fun Url(builder: UrlBuilder): Url = UrlBuilder().takeFrom(builder).build()

/**
 * Construct a [Url] by applying [block] an empty [UrlBuilder].
 */
public fun buildUrl(block: UrlBuilder.() -> Unit): Url = UrlBuilder().apply(block).build()

/**
 * Construct [UrlBuilder] from [urlString].
 */
@Suppress("FunctionName")
public fun URLBuilder(urlString: String): UrlBuilder = UrlBuilder().takeFrom(urlString)

public fun URLBuilder(locator: Locator): UrlBuilder = (locator as? UrlBuilder) ?: UrlBuilder().takeFrom(locator.asUri())

/**
 * Construct [UrlBuilder] from [url].
 */
@Suppress("FunctionName")
public fun URLBuilder(url: Url): UrlBuilder = UrlBuilder().takeFrom(url)

/**
 * Construct [UrlBuilder] from [builder].
 */
@Suppress("FunctionName")
public fun URLBuilder(builder: UrlBuilder): UrlBuilder = UrlBuilder().takeFrom(builder)

/**
 * Take components from another [url] builder
 */
public fun UrlBuilder.takeFrom(url: UrlBuilder): UrlBuilder {
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
 */
public fun UrlBuilder.takeFrom(url: Url): UrlBuilder {
    protocolOrNull = url.protocol
    host = url.host
    port = url.portOrDefault
    pathSegments = url.path.segments
    user = url.authority.user
    password = url.authority.password
    encodedParameters = ParametersBuilder().apply { url.parameters?.let { appendAll(it) } }
    url.fragment?.let { fragment = it }
    trailingQuery = url.parameters != null && url.parameters.isEmpty()

    return this
}

public fun UrlBuilder.takeFrom(uri: UriReference): UrlBuilder {
    protocolOrNull = uri.protocol
    uri.host?.let { host = it }
    uri.port?.let { port = it }
    uri.user?.let { user = it }
    uri.password?.let { password = it }
    if (uri.host == null && uri.path.isRelative()) {
        appendPathSegments(uri.path.segments)
    } else {
        pathSegments = uri.path.segments
    }
    encodedParameters = ParametersBuilder().apply { uri.parameters?.let { appendAll(it) } }
    uri.fragment?.let { fragment = it }
    trailingQuery = uri.parameters?.isEmpty() == true
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
public val Url.hostWithPort: String get() = "$host:$portOrDefault"

/**
 * Returns "host:port" when port is specified. Else, returns host.
 */
public val Url.hostWithPortIfSpecified: String get() =
    when(port) {
        null -> host
        else -> "$host:$port"
    }

public val Url.protocolWithAuthority: String get() = buildString {
    append(protocol)
    append(':')
    if (protocolSeparator != null)
        append("//")
    append(authority)
}

/**
 * Either the specified port from the authority or the default from the protocol.
 */
public val Url.portOrDefault: Int get() =
    authority.port ?: protocol.defaultPort

/**
 * The string value of the path with URL encoding applied.
 */
public val Url.encodedPath: String get() =
    path.toString()


/**
 * The string value of the path with URL encoding applied.
 */
public val Url.encodedPathAndQuery: String get() = fullPath

/**
 * The string value of the query.
 */
public val Url.encodedQuery: String get() = parameters?.formUrlEncode() ?: ""

/**
 * Returns true if URL has ? character and empty query.
 */
public val Url.trailingQuery: Boolean get() = parameters != null && parameters.isEmpty()

/**
 * Returns the list of segments found in the path.
 */
public val Url.pathSegments: List<String> get() = path.segments

/**
 * Returns the user encoded, or empty string.
 */
public val Url.encodedUser: String get() = user?.encodeURLParameter() ?: ""

/**
 * Returns the password encoded, or empty string.
 */
public val Url.encodedPassword: String get() = password?.encodeURLParameter() ?: ""

/**
 * Returns the fragment encoded, or empty string.
 */
public val Url.encodedFragment: String get() = fragment?.encodeURLQueryComponent() ?: ""

/**
 * Get query or empty parameters.
 */
public val Url.parameters: Parameters get() = parameters ?: Parameters.Empty

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
 */
public val Url.isAbsolutePath: Boolean get() = path.isAbsolute()

/**
 * Checks if [Url] has absolute path.
 */
public val Url.isRelativePath: Boolean get() = !isAbsolutePath

/**
 * Checks if [Url] has absolute path.
 */
public val UrlBuilder.isAbsolutePath: Boolean get() = pathSegments.firstOrNull() == ""

/**
 * Checks if [Url] has absolute path.
 */
public val UrlBuilder.isRelativePath: Boolean get() = !isAbsolutePath

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
