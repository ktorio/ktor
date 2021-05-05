/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.response

import io.ktor.http.*
import io.ktor.util.date.*

/**
 * Server's response cookies
 */
public class ResponseCookies(
    private val response: ApplicationResponse,
    private val secureTransport: Boolean
) {
    /**
     * Get cookie from response HTTP headers (from `Set-Cookie` header)
     */
    public operator fun get(name: String): Cookie? = response.headers
        .values("Set-Cookie")
        .map { parseServerSetCookieHeader(it) }
        .firstOrNull { it.name == name }

    /**
     * Append cookie [item] using `Set-Cookie` HTTP response header
     */
    public fun append(item: Cookie) {
        if (item.secure && !secureTransport) {
            throw IllegalArgumentException("You should set secure cookie only via secure transport (HTTPS)")
        }
        response.headers.append("Set-Cookie", renderSetCookieHeader(item))
    }

    /**
     * Append a cookie using `Set-Cookie` HTTP response header from the specified parameters
     */
    @Deprecated("Convert maxAge to Long")
    public fun append(
        name: String,
        value: String,
        encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
        maxAge: Int,
        expires: GMTDate? = null,
        domain: String? = null,
        path: String? = null,
        secure: Boolean = false,
        httpOnly: Boolean = false,
        extensions: Map<String, String?> = emptyMap()
    ) {
        append(name, value, encoding, maxAge.toLong(), expires, domain, path, secure, httpOnly, extensions)
    }

    /**
     * Append a cookie using `Set-Cookie` HTTP response header from the specified parameters
     */
    public fun append(
        name: String,
        value: String,
        encoding: CookieEncoding = CookieEncoding.URI_ENCODING,
        maxAge: Long = 0,
        expires: GMTDate? = null,
        domain: String? = null,
        path: String? = null,
        secure: Boolean = false,
        httpOnly: Boolean = false,
        extensions: Map<String, String?> = emptyMap()
    ) {
        append(
            Cookie(
                name,
                value,
                encoding,
                maxAge.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                expires,
                domain,
                path,
                secure,
                httpOnly,
                extensions
            )
        )
    }

    /**
     * Append already expired cookie: useful to remove client cookies
     */
    public fun appendExpired(name: String, domain: String? = null, path: String? = null) {
        append(name, "", domain = domain, path = path, expires = GMTDate.START)
    }
}
