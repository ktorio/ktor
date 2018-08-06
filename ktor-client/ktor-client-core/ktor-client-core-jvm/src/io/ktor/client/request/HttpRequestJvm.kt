package io.ktor.client.request

import io.ktor.http.*

/**
 * Sets the [HttpRequestBuilder.url] from [url].
 */
fun HttpRequestBuilder.url(url: java.net.URL): Unit = this.url.takeFrom(url)

/**
 * Constructs a [HttpRequestBuilder] from [url].
 */
operator fun HttpRequestBuilder.Companion.invoke(url: java.net.URL): HttpRequestBuilder =
    HttpRequestBuilder().apply { url(url) }