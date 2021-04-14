/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http

import io.ktor.util.*

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
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    parameters.urlEncodingOption = url.parameters.urlEncodingOption
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

/**
 * Take components from another [url]
 */
public fun URLBuilder.takeFrom(url: Url): URLBuilder {
    protocol = url.protocol
    host = url.host
    port = url.specifiedPort
    encodedPath = url.encodedPath
    user = url.user
    password = url.password
    parameters.appendAll(url.parameters)
    parameters.urlEncodingOption = url.parameters.urlEncodingOption
    fragment = url.fragment
    trailingQuery = url.trailingQuery

    return this
}

/**
 * Full encoded path with query string but without domain, port and schema
 */
public val Url.fullPath: String
    get() = buildString { appendUrlFullPath(encodedPath, parameters, trailingQuery) }

/**
 * Host:port pair, not normalized so port is always specified even if the port is schema's default
 */
public val Url.hostWithPort: String get() = "$host:$port"

internal fun Appendable.appendUrlFullPath(
    encodedPath: String,
    queryParameters: Parameters,
    trailingQuery: Boolean
) {
    if (encodedPath.isNotBlank() && !encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!queryParameters.isEmpty() || trailingQuery) {
        append("?")
    }

    queryParameters.formUrlEncodeTo(this)
}

internal fun Appendable.appendUrlFullPath(
    encodedPath: String,
    queryParameters: ParametersBuilder,
    trailingQuery: Boolean
) {
    if (encodedPath.isNotBlank() && !encodedPath.startsWith("/")) {
        append('/')
    }

    append(encodedPath)

    if (!queryParameters.isEmpty() || trailingQuery) {
        append("?")
    }

    queryParameters.formUrlEncodeTo(this)
}
