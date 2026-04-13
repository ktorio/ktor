/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("unused")

package io.ktor.server.request

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.routing.*

/**
 * Get query parameter value associated with [name] or fail with [MissingRequestParameterException]
 *
 * @throws MissingRequestParameterException if no query parameter with [name] is present
 */
public fun ApplicationCall.requireQueryParameter(name: String): String {
    return request.queryParameters[name] ?: throw MissingRequestParameterException(name)
}

/**
 * Get header value associated with [name] or fail with [MissingRequestParameterException]
 *
 * @throws MissingRequestParameterException if no header with [name] is present
 */
public fun ApplicationCall.requireHeader(name: String): String {
    return request.headers[name] ?: throw MissingRequestParameterException(name)
}

/**
 * Get cookie value associated with [name] or fail with [MissingRequestParameterException]
 *
 * @throws MissingRequestParameterException if no cookie with [name] is present
 */
public fun ApplicationCall.requireCookie(
    name: String,
    encoding: CookieEncoding = CookieEncoding.URI_ENCODING
): String {
    return request.cookies[name, encoding] ?: throw MissingRequestParameterException(name)
}

/**
 * Get path parameter value associated with [name] or fail with [MissingRequestParameterException]
 *
 * @throws MissingRequestParameterException if no path parameter with [name] is present
 */
public fun RoutingCall.requirePathParameter(name: String): String {
    return pathParameters[name] ?: throw MissingRequestParameterException(name)
}
