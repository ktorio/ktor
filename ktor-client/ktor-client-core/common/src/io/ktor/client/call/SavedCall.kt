/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import kotlin.coroutines.CoroutineContext

/**
 * Saves the entire content of this [HttpClientCall] to memory and returns a new [HttpClientCall]
 * with the content cached in memory.
 * This can be particularly useful for caching, debugging,
 * or processing responses without relying on the original network stream.
 *
 * By caching the content, this function simplifies the management of the [HttpResponse] lifecycle.
 * It releases the network connection and other resources associated with the original [HttpResponse],
 * ensuring they are no longer required to be explicitly closed.
 *
 * This behavior is automatically applied to non-streaming [HttpResponse] instances.
 * For streaming responses, this function allows you to convert them into a memory-based representation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.call.save)
 *
 * @return A new [HttpClientCall] instance with all its content stored in memory.
 */
public suspend fun HttpClientCall.save(): HttpClientCall = save(allowMissingBody = false)

/**
 * Saves the call like [HttpClientCall.save].
 *
 * If [allowMissingBody] is `true`, an empty body passes the `Content-Length` check, also when `Content-Length`
 * isn't zero. A body that isn't empty must still match `Content-Length`.
 *
 * Set [allowMissingBody] for engines that report an empty body because they can't read it. An example is
 * a WebSocket handshake that the platform WebSocket client rejects.
 */
@OptIn(InternalAPI::class)
internal suspend fun HttpClientCall.save(allowMissingBody: Boolean): HttpClientCall {
    if (this is SavedHttpCall) return this

    val responseBody = response.rawContent.readBuffer().readByteArray()
    val skipContentLengthCheck = allowMissingBody && responseBody.isEmpty()
    return SavedHttpCall(client, request, response, responseBody, skipContentLengthCheck = skipContentLengthCheck)
}

internal class SavedHttpCall(
    client: HttpClient,
    request: HttpRequest,
    response: HttpResponse,
    responseBody: ByteArray,
    skipContentLengthCheck: Boolean = false,
) : HttpClientCall(client) {

    init {
        this.request = SavedHttpRequest(this, request)
        this.response = SavedHttpResponse(this, responseBody, response)

        if (!skipContentLengthCheck) {
            checkContentLength(response.contentLength(), responseBody.size.toLong(), request.method)
        }
    }

    override val allowDoubleReceive: Boolean = true
}

internal class SavedHttpRequest(
    override val call: SavedHttpCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class SavedHttpResponse(
    override val call: SavedHttpCall,
    private val body: ByteArray,
    origin: HttpResponse
) : HttpResponse() {
    override val status: HttpStatusCode = origin.status

    override val version: HttpProtocolVersion = origin.version

    override val requestTime: GMTDate = origin.requestTime

    override val responseTime: GMTDate = origin.responseTime

    override val headers: Headers = origin.headers

    override val coroutineContext: CoroutineContext = origin.coroutineContext

    @OptIn(InternalAPI::class)
    override val rawContent: ByteReadChannel get() = ByteReadChannel(body)
}
