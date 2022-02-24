/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.request

import io.ktor.http.*
import io.ktor.util.date.*

/**
 * Gets the associated URL's host.
 */
public var HttpRequestBuilder.host: String
    get() = url.host
    set(value) {
        url.host = value
    }

/**
 * Gets the associated URL's port.
 */
public var HttpRequestBuilder.port: Int
    get() = url.port
    set(value) {
        url.port = value
    }

/**
 * Sets a single header of [key] with a specific [value] if the value is not null.
 */
public fun HttpRequestBuilder.header(key: String, value: Any?): Unit =
    value?.let { headers.append(key, it.toString()) } ?: Unit

/**
 * Sets a single header of [key] with a specific [value] if the value is not null.
 */
public fun HttpRequestBuilder.cookie(
    name: String,
    value: String,
    maxAge: Int = 0,
    expires: GMTDate? = null,
    domain: String? = null,
    path: String? = null,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    extensions: Map<String, String?> = emptyMap()
): Unit { // ktlint-disable no-unit-return
    val renderedCookie = Cookie(
        name = name,
        value = value,
        maxAge = maxAge,
        expires = expires,
        domain = domain,
        path = path,
        secure = secure,
        httpOnly = httpOnly,
        extensions = extensions
    ).let(::renderCookieHeader)

    if (HttpHeaders.Cookie !in headers) {
        headers.append(HttpHeaders.Cookie, renderedCookie)
        return
    }
    // Client cookies are stored in a single header "Cookies" and multiple values are separated with ";"
    headers[HttpHeaders.Cookie] = headers[HttpHeaders.Cookie] + "; " + renderedCookie
}

/**
 * Sets a single URL query parameter of [key] with a specific [value] if the value is not null. Can not be used to set
 * form parameters in the body.
 */
public fun HttpRequestBuilder.parameter(key: String, value: Any?): Unit =
    value?.let { url.parameters.append(key, it.toString()) } ?: Unit

/**
 * Sets the `Accept` header with a specific [contentType].
 */
public fun HttpRequestBuilder.accept(contentType: ContentType): Unit =
    headers.append(HttpHeaders.Accept, contentType.toString())
