/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http

import java.net.*

/**
 * Take URI components from [uri]
 */
fun URLBuilder.takeFrom(uri: URI): URLBuilder {
    uri.scheme?.let {
        protocol = URLProtocol.createOrDefault(it)
        port = protocol.defaultPort
    }

    if (uri.port > 0) {
        port = uri.port
    } else {
        when (uri.scheme) {
            "http" -> port = 80
            "https" -> port = 443
        }
    }

    if (uri.userInfo != null && uri.userInfo.isNotEmpty()) {
        val parts = uri.userInfo.split(":")
        user = parts.first()
        password = parts.getOrNull(1)
    }

    uri.host?.let { host = it }
    encodedPath = uri.rawPath
    uri.query?.let { parameters.appendAll(parseQueryString(it)) }
    if (uri.query?.isEmpty() == true) {
        trailingQuery = true
    }

    uri.fragment?.let { fragment = it }
    return this
}

/**
 * Take URI components from [url]
 */
fun URLBuilder.takeFrom(url: URL) = takeFrom(url.toURI())

/**
 * Convert [Url] to [URI]
 */
fun Url.toURI(): URI = URI(toString())

/**
 * Helper method that concisely creates a [Url] from a [URI]
 *
 * Creates [Url] from [URI]
 */
@Suppress("FunctionName")
fun Url(uri: URI): Url = URLBuilder().takeFrom(uri).build()
