package io.ktor.client.response

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.parseServerSetCookieHeader


fun HttpResponseBuilder.contentType(): ContentType? =
        headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

fun HttpResponseBuilder.cookies(): List<Cookie> =
        headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: listOf()
