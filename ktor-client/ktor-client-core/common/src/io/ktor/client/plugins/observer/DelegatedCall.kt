/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.observer

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*

internal class DelegatedRequest(
    override val call: HttpClientCall,
    origin: HttpRequest
) : HttpRequest by origin

internal class DelegatedResponse @InternalAPI constructor(
    override val call: HttpClientCall,
    origin: HttpResponse,
    @InternalAPI
    override val body: HttpResponseBody = origin.body,
    override val headers: Headers = origin.headers,
) : HttpResponse by origin

@InternalAPI
internal class DelegatedCall @OptIn(InternalAPI::class) constructor(
    call: HttpClientCall,
    body: HttpResponseBody = call.response.body,
    headers: Headers = call.response.headers,
): HttpClientCall(call.client) {
    init {
        request = DelegatedRequest(this, call.request)
        response = DelegatedResponse(this, call.response, body, headers)
    }
}

@OptIn(InternalAPI::class)
public fun HttpClientCall.withResponseBody(channel: ByteReadChannel): HttpClientCall =
    DelegatedCall(this, HttpResponseBody.create(channel))

@OptIn(InternalAPI::class)
public fun HttpClientCall.withResponseBody(body: HttpResponseBody): HttpClientCall =
    DelegatedCall(this, body)

@OptIn(InternalAPI::class)
public fun HttpClientCall.withBodyAndHeaders(body: HttpResponseBody, headers: Headers): HttpClientCall =
    DelegatedCall(this, body, headers)

@OptIn(InternalAPI::class)
public fun HttpResponse.withResponseBody(body: HttpResponseBody): HttpResponse =
    DelegatedCall(call, body).response

@OptIn(InternalAPI::class)
public suspend fun HttpResponse.copied(): HttpResponse =
    DelegatedCall(call, body.copy()).response

