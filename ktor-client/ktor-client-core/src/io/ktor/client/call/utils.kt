package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.content.*
import io.ktor.http.*
import java.net.*


class UnsupportedContentTypeException(content: OutgoingContent)
    : IllegalStateException("Failed to write body: ${content::class}")

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and
 * with the specified HTTP request [builder].
 */
suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall = call { takeFrom(builder) }

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall = call {
    this.url.takeFrom(url)
    block()
}

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(URL(url), block)
