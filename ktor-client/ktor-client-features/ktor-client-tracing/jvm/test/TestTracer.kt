/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

import io.ktor.client.features.tracing.*
import io.ktor.client.request.*
import io.ktor.http.cio.websocket.*

class TestTracer : Tracer {
    val requestWillBeSentCalls = mutableListOf<RequestWillBeSentCall>()
    val responseHeadersReceivedCalls = mutableListOf<ResponseHeadersReceivedCall>()
    val interpretResponseCalls = mutableListOf<InterpretResponseCall>()
    val httpExchangeFailedCalls = mutableListOf<HttpExchangeFailedCall>()
    val responseReadFinishedCalls = mutableListOf<ResponseReadFinishedCall>()
    val webSocketCreatedCalls = mutableListOf<WebSocketCreatedCall>()
    val webSocketWillSendHandshakeRequestCalls = mutableListOf<WebSocketWillSendHandshakeRequestCall>()
    val webSocketHandshakeResponseReceivedCalls = mutableListOf<WebSocketHandshakeResponseReceivedCall>()
    val webSocketFrameSentCalls = mutableListOf<WebSocketFrameSentCall>()
    val webSocketFrameReceivedCalls = mutableListOf<WebSocketFrameReceivedCall>()
    val webSocketClosedCalls = mutableListOf<WebSocketClosedCall>()

    override fun requestWillBeSent(requestId: String, requestData: HttpRequestData) {
        synchronized(this) {
            requestWillBeSentCalls += RequestWillBeSentCall(
                requestId,
                requestData
            )
        }
    }

    override fun responseHeadersReceived(
        requestId: String,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    ) {
        synchronized(this) {
            responseHeadersReceivedCalls += ResponseHeadersReceivedCall(
                requestId,
                requestData,
                responseData
            )
        }
    }

    override fun interpretResponse(
        requestId: String,
        contentType: String?,
        contentEncoding: String?,
        body: Any?
    ): Any? {
        synchronized(this) {
            interpretResponseCalls += InterpretResponseCall(
                requestId,
                contentType,
                contentEncoding,
                body
            )
            return body
        }
    }

    override fun httpExchangeFailed(requestId: String, message: String) {
        synchronized(this) {
            httpExchangeFailedCalls += HttpExchangeFailedCall(
                requestId,
                message
            )
        }
    }

    override fun responseReadFinished(requestId: String) {
        synchronized(this) {
            responseReadFinishedCalls += ResponseReadFinishedCall(
                requestId
            )
        }
    }

    override fun webSocketCreated(requestId: String, url: String) {
        webSocketCreatedCalls += WebSocketCreatedCall(requestId, url)
    }

    override fun webSocketWillSendHandshakeRequest(requestId: String, requestData: HttpRequestData) {
        webSocketWillSendHandshakeRequestCalls += WebSocketWillSendHandshakeRequestCall(
            requestId,
            requestData
        )
    }

    override fun webSocketHandshakeResponseReceived(
        requestId: String,
        requestData: HttpRequestData,
        responseData: HttpResponseData
    ) {
        webSocketHandshakeResponseReceivedCalls += WebSocketHandshakeResponseReceivedCall(
            requestId,
            requestData,
            responseData
        )
    }

    override fun webSocketFrameSent(requestId: String, frame: Frame) {
        webSocketFrameSentCalls += WebSocketFrameSentCall(
            requestId,
            frame
        )
    }

    override fun webSocketFrameReceived(requestId: String, frame: Frame) {
        webSocketFrameReceivedCalls += WebSocketFrameReceivedCall(
            requestId,
            frame
        )
    }

    override fun webSocketClosed(requestId: String) {
        webSocketClosedCalls += WebSocketClosedCall(requestId)
    }
}

data class RequestWillBeSentCall(val requestId: String, val requestData: HttpRequestData)

data class ResponseHeadersReceivedCall(
    val requestId: String, val requestData: HttpRequestData,
    val responseData: HttpResponseData
)

data class InterpretResponseCall(
    val requestId: String, val contentType: String?,
    val contentEncoding: String?,
    val body: Any?
)

data class HttpExchangeFailedCall(val requestId: String, val message: String)

data class ResponseReadFinishedCall(val requestId: String)

data class WebSocketCreatedCall(val requestId: String, val url: String)

data class WebSocketWillSendHandshakeRequestCall(val requestId: String, val requestData: HttpRequestData)

data class WebSocketHandshakeResponseReceivedCall(
    val requestId: String, val requestData: HttpRequestData,
    val responseData: HttpResponseData
)

data class WebSocketFrameSentCall(val requestId: String, val frame: Frame)

data class WebSocketFrameReceivedCall(val requestId: String, val frame: Frame)

data class WebSocketClosedCall(val requestId: String)
