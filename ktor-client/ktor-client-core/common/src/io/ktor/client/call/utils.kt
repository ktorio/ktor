package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*


@Suppress("KDocMissingDocumentation")
class UnsupportedContentTypeException(content: OutgoingContent) :
    IllegalStateException("Failed to write body: ${content::class}")

@Suppress("KDocMissingDocumentation")
class UnsupportedUpgradeProtocolException(
    url: Url
) : IllegalArgumentException("Unsupported upgrade protocol exception: $url")

/**
 * Constructs a [HttpClientCall] from this [HttpClient] and
 * with the specified HTTP request [builder].
 */
suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall = call { takeFrom(builder) }

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
suspend fun HttpClient.call(
    urlString: String,
    block: suspend HttpRequestBuilder.() -> Unit = {}
): HttpClientCall = call {
    url.takeFrom(urlString)
    block()
}

/**
 * Constructs a [HttpClientCall] from this [HttpClient],
 * an [url] and an optional [block] configuring a [HttpRequestBuilder].
 */
suspend fun HttpClient.call(
    url: Url,
    block: suspend HttpRequestBuilder.() -> Unit = {}
): HttpClientCall = call {
    this.url.takeFrom(url)
    block()
}
