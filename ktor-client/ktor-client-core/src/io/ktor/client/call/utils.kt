package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.content.*


class UnsupportedContentTypeException(content: OutgoingContent) :
    IllegalStateException("Failed to write body: ${content::class}")

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and
 * with the specified HTTP request [builder].
 */
suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall = call { takeFrom(builder) }
