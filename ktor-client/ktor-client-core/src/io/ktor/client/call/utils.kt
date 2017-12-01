package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.content.*
import io.ktor.http.*
import java.net.*


class UnsupportedContentTypeException(content: OutgoingContent)
    : IllegalStateException("Failed to write body: ${content::class}")

suspend fun HttpClient.call(builder: HttpRequestBuilder): HttpClientCall = call { takeFrom(builder) }

suspend fun HttpClient.call(url: URL, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall = call {
    this.url.takeFrom(url)
    block()
}

suspend fun HttpClient.call(url: String, block: HttpRequestBuilder.() -> Unit = {}): HttpClientCall =
        call(URL(decodeURLPart(url)), block)
