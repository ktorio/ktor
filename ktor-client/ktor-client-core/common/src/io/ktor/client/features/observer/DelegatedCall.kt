/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.observer

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

/**
 * Wrap existing [HttpClientCall] with new [content].
 */
@Deprecated(
    "Parameter [shouldCloseOrigin] is deprecated",
    ReplaceWith("wrapWithContent(content)"),
    level = DeprecationLevel.ERROR
)
public fun HttpClientCall.wrapWithContent(
    content: ByteReadChannel,
    shouldCloseOrigin: Boolean
): HttpClientCall = wrapWithContent(content)

/**
 * Wrap existing [HttpClientCall] with new [content].
 */
public fun HttpClientCall.wrapWithContent(content: ByteReadChannel): HttpClientCall {
    val currentClient = client ?: error("Fail to create response observer in different native thread.")

    return DelegatedCall(currentClient, content, this)
}

internal class DelegatedCall(
    client: HttpClient,
    content: ByteReadChannel,
    originCall: HttpClientCall
) : HttpClientCall(client) {

    init {
        request = DelegatedRequest(this, originCall.request)
        response = DelegatedResponse(this, content, originCall.response)
    }
}

internal class DelegatedRequest(
    override val call: HttpClientCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class DelegatedResponse(
    override val call: HttpClientCall,
    override val content: ByteReadChannel,
    private val origin: HttpResponse
) : HttpResponse() {
    override val coroutineContext: CoroutineContext = origin.coroutineContext

    override val status: HttpStatusCode get() = origin.status

    override val version: HttpProtocolVersion get() = origin.version

    override val requestTime: GMTDate get() = origin.requestTime

    override val responseTime: GMTDate get() = origin.responseTime

    override val headers: Headers get() = origin.headers
}
