/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.internal.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.util.date.*
import io.ktor.utils.io.*

internal class CurlClientEngine(
    override val config: CurlClientEngineConfig
) : HttpClientEngineBase("ktor-curl") {

    override val supportedCapabilities = setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    private val curlProcessor = CurlProcessor(coroutineContext)

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val requestTime = GMTDate()

        val curlRequest = data.toCurlRequest(config)
        val responseData = curlProcessor.executeRequest(curlRequest)

        return with(responseData) {
            val headerBytes = ByteReadChannel(headersBytes).apply {
                // Skip status line
                readLineStrict()
            }
            val rawHeaders = parseHeaders(headerBytes)
            val headers = rawHeaders
                .toBuilder().apply {
                    dropCompressionHeaders(data.method, data.attributes)
                }.build()

            rawHeaders.release()

            val status = HttpStatusCode.fromValue(status)

            val responseBody: Any = if (data.isUpgradeRequest()) {
                val websocket = responseBody as CurlWebSocketResponseBody
                CurlWebSocketSession(websocket, callContext)
            } else {
                val httpResponse = responseBody as CurlHttpResponseBody
                data.attributes.getOrNull(ResponseAdapterAttributeKey)
                    ?.adapt(data, status, headers, httpResponse.bodyChannel, data.body, callContext)
                    ?: httpResponse.bodyChannel
            }

            HttpResponseData(
                status,
                requestTime,
                headers,
                version.fromCurl(),
                responseBody,
                callContext
            )
        }
    }

    override fun close() {
        super.close()
        curlProcessor.close()
    }
}

@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
public class CurlIllegalStateException(cause: String) : IllegalStateException(cause)

@Deprecated("This exception will be removed in a future release in favor of a better error handling.")
public class CurlRuntimeException(cause: String) : RuntimeException(cause)
