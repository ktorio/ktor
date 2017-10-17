package io.ktor.client.response

import io.ktor.http.*


fun HttpResponseBuilder.contentType(): ContentType? =
        headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }

fun HttpResponseBuilder.cookies(): List<Cookie> =
        headers.getAll(HttpHeaders.SetCookie)?.map { parseServerSetCookieHeader(it) } ?: listOf()
