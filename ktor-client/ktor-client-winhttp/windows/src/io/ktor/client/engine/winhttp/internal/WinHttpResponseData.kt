/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlin.coroutines.*

internal class WinHttpResponseData(
    val statusCode: Int,
    val httpProtocol: String,
    val headers: String
)

@OptIn(InternalAPI::class)
internal suspend fun WinHttpResponseData.convert(
    data: HttpRequestData,
    requestTime: GMTDate,
    body: Any,
    callContext: CoroutineContext
): HttpResponseData {
    val status = HttpStatusCode.fromValue(statusCode)
    val headers = parseResponse(ByteReadChannel(headers))?.use { response ->
        HeadersImpl(response.headers.toMap())
    } ?: throw IllegalStateException("Failed to parse response header")

    val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
        ?.adapt(data, status, headers, body as ByteReadChannel, data.body, callContext)
        ?: body

    return HttpResponseData(
        status,
        requestTime,
        headers,
        HttpProtocolVersion.parse(httpProtocol),
        responseBody,
        callContext
    )
}

private fun HttpHeadersMap.toMap(): Map<String, List<String>> {
    val result = mutableMapOf<String, MutableList<String>>()

    for (index in 0 until size) {
        val key = nameAt(index).toString()
        val value = valueAt(index).toString()

        if (result[key]?.add(value) == null) {
            result[key] = mutableListOf(value)
        }
    }

    return result
}
