/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

internal class SavedHttpCall(
    client: HttpClient,
    request: HttpRequest,
    response: HttpResponse,
    responseBody: ByteArray
) : HttpClientCall(client) {

    init {
        this.request = SavedHttpRequest(this, request)
        this.response = SavedHttpResponse(this, responseBody, response)
    }
}

internal class SavedHttpRequest(
    override val call: SavedHttpCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class SavedHttpResponse(
    override val call: SavedHttpCall,
    body: ByteArray,
    origin: HttpResponse
) : HttpResponse by origin {
    private val context = Job()

    override val coroutineContext: CoroutineContext = origin.coroutineContext + context

    @OptIn(InternalAPI::class)
    override val body = HttpResponseBody.create(body)
}

/**
 * Fetch data for [HttpClientCall] and close the origin.
 */
@OptIn(InternalAPI::class)
public suspend fun HttpClientCall.save(): HttpClientCall =
    SavedHttpCall(client, request, response, response.body.read {
        readRemaining().readByteArray()
    })
