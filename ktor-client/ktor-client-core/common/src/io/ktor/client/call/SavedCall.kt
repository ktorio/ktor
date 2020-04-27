/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.call

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.coroutines.*


internal class SavedHttpCall(client: HttpClient) : HttpClientCall(client)

internal class SavedHttpRequest(
    override val call: SavedHttpCall, origin: HttpRequest
) : HttpRequest by origin

internal class SavedHttpResponse(
    override val call: SavedHttpCall, body: ByteArray, origin: HttpResponse
) : HttpResponse() {
    override val status: HttpStatusCode = origin.status

    override val version: HttpProtocolVersion = origin.version

    override val requestTime: GMTDate = origin.requestTime

    override val responseTime: GMTDate = origin.responseTime

    override val headers: Headers = origin.headers

    override val coroutineContext: CoroutineContext = origin.coroutineContext

    override val content: ByteReadChannel = ByteReadChannel(body)
}

/**
 * Fetch data for [HttpClientCall] and close the origin.
 */
@KtorExperimentalAPI
suspend fun HttpClientCall.save(): HttpClientCall = SavedHttpCall(client).also { result ->
    val content = response.content.readRemaining()
    result.request = SavedHttpRequest(result, request)
    result.response = SavedHttpResponse(result, content.readBytes(), response)
}
