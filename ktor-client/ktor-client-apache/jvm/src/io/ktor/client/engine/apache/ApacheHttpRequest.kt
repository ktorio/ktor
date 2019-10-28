/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.apache

import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.apache.http.concurrent.*
import org.apache.http.impl.nio.client.*
import java.net.*
import kotlin.coroutines.*

internal suspend fun CloseableHttpAsyncClient.sendRequest(
    request: ApacheRequestProducer,
    callContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData = suspendCancellableCoroutine { continuation ->
    val requestTime = GMTDate()

    val consumer = ApacheResponseConsumerDispatching(callContext, requestData) { rawResponse, body ->
        val statusLine = rawResponse.statusLine

        val status = HttpStatusCode(statusLine.statusCode, statusLine.reasonPhrase)
        val version = with(rawResponse.protocolVersion) { HttpProtocolVersion.fromValue(protocol, major, minor) }
        val headers = Headers.build {
            rawResponse.allHeaders.forEach { headerLine ->
                append(headerLine.name, headerLine.value)
            }
        }
        val result = HttpResponseData(status, requestTime, headers, version, body, callContext)
        continuation.resume(result)
    }

    val callback = object : FutureCallback<Unit> {
        override fun failed(exception: Exception) {
            val mappedCause = when {
                exception is ConnectException && exception.isTimeoutException() -> HttpConnectTimeoutException(
                    requestData
                )
                exception is SocketTimeoutException -> HttpSocketTimeoutException(requestData)
                else -> exception
            }

            callContext.cancel(CancellationException("Failed to execute request", mappedCause))
            continuation.cancel(exception)
        }

        override fun completed(result: Unit) {}

        override fun cancelled() {
            callContext.cancel()
            continuation.cancel()
        }
    }

    execute(request, consumer, callback).apply {
        // We need to cancel Apache future if it's not needed anymore.
        continuation.invokeOnCancellation {
            cancel(true)
        }
    }
}
