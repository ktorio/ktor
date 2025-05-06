/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext

/**
 * Replaces response of the [HttpClientCall] substituting [headers] and [content].
 * Returns a new [HttpClientCall] containing this response.
 *
 * The [content] function will be called each time the response content is requested.
 * This function should return a new [ByteReadChannel] instance on each call
 * if the response content should be replayable.
 *
 * Example usage:
 * ```
 * // Content decompression. See ContentEncoding implementation for full example
 * val decodedCall = originalCall.replaceResponse(
 *     headers = headersOf("Content-Encoding", "identity")
 * ) { // this: HttpResponse ->
 *     decodeGzip(rawContent) // returns a new ByteReadChannel with decoded content
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.call.replaceResponse)
 */
public fun HttpClientCall.replaceResponse(
    headers: Headers = response.headers,
    content: HttpResponse.() -> ByteReadChannel,
): HttpClientCall {
    return DelegatedCall(client, this, content, headers)
}

internal class DelegatedCall(
    client: HttpClient,
    originCall: HttpClientCall,
    responseContent: HttpResponse.() -> ByteReadChannel,
    responseHeaders: Headers = originCall.response.headers
) : HttpClientCall(client) {

    init {
        request = DelegatedRequest(this, originCall.request)
        response = DelegatedResponse(this, originCall.response, responseContent, responseHeaders)
    }
}

internal class DelegatedRequest(
    override val call: HttpClientCall,
    origin: HttpRequest
) : HttpRequest by origin

@OptIn(InternalAPI::class)
internal class DelegatedResponse(
    override val call: HttpClientCall,
    private val origin: HttpResponse,
    private val content: HttpResponse.() -> ByteReadChannel,
    override val headers: Headers = origin.headers
) : HttpResponse() {

    override val rawContent: ByteReadChannel get() = origin.content()

    override val coroutineContext: CoroutineContext = origin.coroutineContext

    override val status: HttpStatusCode get() = origin.status

    override val version: HttpProtocolVersion get() = origin.version

    override val requestTime: GMTDate get() = origin.requestTime

    override val responseTime: GMTDate get() = origin.responseTime
}
