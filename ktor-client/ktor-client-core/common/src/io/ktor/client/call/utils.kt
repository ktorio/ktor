/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import io.ktor.utils.io.*


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

internal fun HttpResponse.channelWithCloseHandling(): ByteReadChannel = writer {
    try {
        content.joinTo(channel, closeOnEnd = true)
    } catch (cause: CancellationException) {
        this@channelWithCloseHandling.cancel(cause)
    } finally {
        this@channelWithCloseHandling.close()
    }
}.channel
