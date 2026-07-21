/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cache

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

internal suspend fun refreshNotModifiedResponse(
    request: HttpRequest,
    response: HttpResponse,
    findAndRefresh: suspend (HttpRequest, HttpResponse) -> HttpResponse?,
): HttpResponse {
    val responseFromCache = findAndRefresh(request, response)
        ?: throw InvalidCacheStateException(request.url)

    if (responseFromCache.varyKeys().size != response.varyKeys().size) {
        LOGGER.warn(
            "Vary header mismatch on cached response for ${request.url}. " +
                "Received 304 Not Modified with Vary: ${response.varyKeys()} " +
                "but cached response has Vary: ${responseFromCache.varyKeys()}. " +
                "According to RFC 9110 §15.4.5 and RFC 9111 §4.1, " +
                "the server must include the full Vary header in 304 responses. " +
                "Proceeding with cached response despite Vary mismatch. " +
                "Consider reporting this issue to the server maintainers."
        )
    }

    return responseFromCache
}

internal fun <E> List<E>.selectResponseToFreshen(response: HttpResponse, headers: (E) -> Headers): E? {
    if (response.headers.haveNoValidator() && size == 1) {
        return first().takeIf { headers(it).haveNoValidator() }
    }
    val etag = response.headers[HttpHeaders.ETag] ?: return null
    return firstOrNull {
        val cachedEtag = headers(it)[HttpHeaders.ETag]
        cachedEtag != null && etagMatches(cachedEtag, etag)
    }
}

private fun Headers.haveNoValidator(): Boolean =
    HttpHeaders.ETag !in this && HttpHeaders.LastModified !in this
