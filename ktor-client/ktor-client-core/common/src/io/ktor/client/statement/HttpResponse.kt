/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.statement

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import kotlinx.io.*

/**
 * An [HttpClient]'s response, a second part of [HttpClientCall].
 *
 * Learn more from [Receiving responses](https://ktor.io/docs/response.html).
 */
public interface HttpResponse : HttpMessage, CoroutineScope {
    /**
     * The associated [HttpClientCall] containing both
     * the underlying [HttpClientCall.request] and [HttpClientCall.response].
     */
    public val call: HttpClientCall

    /**
     * The [HttpStatusCode] returned by the server. It includes both,
     * the [HttpStatusCode.description] and the [HttpStatusCode.value] (code).
     */
    public val status: HttpStatusCode

    /**
     * HTTP version. Usually [HttpProtocolVersion.HTTP_1_1] or [HttpProtocolVersion.HTTP_2_0].
     */
    public val version: HttpProtocolVersion

    /**
     * [GMTDate] of the request start.
     */
    public val requestTime: GMTDate

    /**
     * [GMTDate] of the response start.
     */
    public val responseTime: GMTDate

    /**
     * Unmodified [ByteReadChannel] with the raw payload of the response.
     *
     * **Note:** this content doesn't go through any interceptors from [HttpResponsePipeline].
     * If you need the modified content, use the [bodyChannel] function.
     */
    @InternalAPI
    public val body: HttpResponseBody
}

public suspend fun HttpResponseBody.readText(charset: Charset = Charsets.UTF_8, max: Int = Int.MAX_VALUE): String =
    read { readRemaining().readText(charset, max) }

public suspend fun HttpResponseBody.readBytes(): ByteArray =
    read { readRemaining().readByteArray() }

public suspend fun HttpResponseBody.readBytes(count: Int): ByteArray =
    ByteArray(count).also {
        read { readFully(it) }
    }

/**
 * Gets [HttpRequest] associated with this response.
 */
public val HttpResponse.request: HttpRequest get() = call.request

@InternalAPI
@PublishedApi
internal fun HttpResponse.complete() {
    val job = coroutineContext[Job]!! as CompletableJob
    job.complete()
}

/**
 * Reads the [HttpResponse.body] as a String. You can pass an optional [charset]
 * to specify a charset in the case no one is specified as part of the `Content-Type` response.
 * If no charset specified either as parameter or as part of the response,
 * [io.ktor.client.plugins.HttpPlainText] settings will be used.
 *
 * Note that [fallbackCharset] parameter will be ignored if the response already has a charset.
 *      So it just acts as a fallback, honoring the server preference.
 */

public suspend fun HttpResponse.bodyAsText(fallbackCharset: Charset = Charsets.UTF_8): String {
    val originCharset = charset() ?: fallbackCharset
    val decoder = originCharset.newDecoder()
    val input = body<Source>()

    return decoder.decode(input)
}

/**
 * Reads the [HttpResponse.body] as a [ByteReadChannel].
 */
@OptIn(InternalAPI::class)
public fun HttpResponse.bodyAsChannel(): ByteReadChannel = body.toChannel()
