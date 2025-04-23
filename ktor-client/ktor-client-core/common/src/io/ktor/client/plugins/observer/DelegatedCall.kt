/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext

/**
 * Wrap existing [HttpClientCall] with new [content].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrapWithContent)
 */
public fun HttpClientCall.wrapWithContent(content: ByteReadChannel): HttpClientCall {
    return DelegatedCall(client, content, this)
}

/**
 * Wrap existing [HttpClientCall] with new content produced by the given [block].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrapWithContent)
 */
public fun HttpClientCall.wrapWithContent(block: () -> ByteReadChannel): HttpClientCall {
    return DelegatedCall(client, block, this)
}

/**
 * Wrap existing [HttpClientCall] with new response [content] and [headers].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrap)
 */
public fun HttpClientCall.wrap(content: ByteReadChannel, headers: Headers): HttpClientCall {
    return DelegatedCall(client, content, this, headers)
}

/**
 * Wrap existing [HttpClientCall] with new [headers] and content produced by the given [block].
 * The [block] will be called each time the response content is requested.
 *
 * ```
 * // Example: Content decompression. See ContentEncoding implementation for full example
 * val originalContent = originalCall.response.content
 * val decodedCall = originalCall.wrap(headersOf("Content-Encoding", "identity")) {
 *     decodeGzip(originalContent) // returns a new ByteReadChannel with decoded content
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.observer.wrap)
 */
@InternalAPI
public fun HttpClientCall.wrap(headers: Headers, block: () -> ByteReadChannel): HttpClientCall {
    return DelegatedCall(client, block, this, headers)
}

internal class DelegatedCall(
    client: HttpClient,
    block: () -> ByteReadChannel,
    originCall: HttpClientCall,
    responseHeaders: Headers = originCall.response.headers
) : HttpClientCall(client) {

    constructor(
        client: HttpClient,
        content: ByteReadChannel,
        originCall: HttpClientCall,
        responseHeaders: Headers = originCall.response.headers
    ) : this(client, { content }, originCall, responseHeaders)

    init {
        request = DelegatedRequest(this, originCall.request)
        response = DelegatedResponse(this, block, originCall.response, responseHeaders)
    }
}

internal class DelegatedRequest(
    override val call: HttpClientCall,
    origin: HttpRequest
) : HttpRequest by origin

@OptIn(InternalAPI::class)
internal class DelegatedResponse(
    override val call: HttpClientCall,
    private val block: () -> ByteReadChannel,
    private val origin: HttpResponse,
    override val headers: Headers = origin.headers
) : HttpResponse() {

    override val rawContent: ByteReadChannel get() = block()

    override val coroutineContext: CoroutineContext = origin.coroutineContext

    override val status: HttpStatusCode get() = origin.status

    override val version: HttpProtocolVersion get() = origin.version

    override val requestTime: GMTDate get() = origin.requestTime

    override val responseTime: GMTDate get() = origin.responseTime
}
