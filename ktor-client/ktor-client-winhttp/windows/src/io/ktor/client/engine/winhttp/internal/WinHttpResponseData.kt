/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.client.engine.winhttp.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.coroutines.*

internal class WinHttpResponseData(
    val statusCode: Int,
    val httpProtocol: String,
    val headers: String
)

internal suspend fun WinHttpResponseData.convert(
    requestTime: GMTDate,
    responseBody: Any,
    callContext: CoroutineContext
): HttpResponseData {
    val headers = parseResponse(ByteReadChannel(headers))?.use { response ->
        HeadersImpl(response.headers.toMap())
    } ?: throw WinHttpIllegalStateException("Failed to parse response header")

    return HttpResponseData(
        HttpStatusCode.fromValue(statusCode),
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
