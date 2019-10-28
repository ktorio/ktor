/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.client.engine.js.compatible.*
import io.ktor.client.features.websocket.*
import io.ktor.client.features.websocket.JsWebSocketSession
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import org.w3c.fetch.Headers
import kotlin.coroutines.*

internal class JsClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngineBase("ktor-js") {

    override val dispatcher = Dispatchers.Default

    init {
        check(config.proxy == null) { "Proxy unsupported in Js engine." }
    }

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()!!

        if (data.isUpgradeRequest()) {
            return executeWebSocketRequest(data, callContext)
        }

        val requestTime = GMTDate()
        val rawRequest = data.toRaw(callContext)
        val rawResponse = fetch(data.url.toString(), rawRequest)

        val status = HttpStatusCode(rawResponse.status.toInt(), rawResponse.statusText)
        val headers = rawResponse.headers.mapToKtor()
        val version = HttpProtocolVersion.HTTP_1_1

        return HttpResponseData(
            status,
            requestTime,
            headers, version,
            readBody(rawResponse, callContext),
            callContext
        )
    }

    private suspend fun executeWebSocketRequest(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()

        val urlString = request.url.toString()
        val socket: WebSocket = if (PlatformUtils.IS_NODE) {
            val ws = js("require('ws')")
            js("new ws(urlString)")
        } else {
            js("new WebSocket(urlString)")
        }

        try {
            socket.awaitConnection()
        } catch (cause: Throwable) {
            callContext.cancel(CancellationException("Failed to connect to $urlString", cause))
            throw cause
        }

        val session = JsWebSocketSession(callContext, socket)

        return HttpResponseData(
            HttpStatusCode.OK,
            requestTime,
            io.ktor.http.Headers.Empty,
            HttpProtocolVersion.HTTP_1_1,
            session,
            callContext
        )
    }
}

private suspend fun WebSocket.awaitConnection(): WebSocket = suspendCancellableCoroutine { continuation ->
    if (continuation.isCancelled) return@suspendCancellableCoroutine

    val eventListener = { event: Event ->
        when (event.type) {
            "open" -> continuation.resume(this)
            "error" -> continuation.resumeWithException(WebSocketException("$event"))
        }
    }

    addEventListener("open", callback = eventListener)
    addEventListener("error", callback = eventListener)

    continuation.invokeOnCancellation {
        removeEventListener("open", callback = eventListener)
        removeEventListener("error", callback = eventListener)

        if (it != null) {
            this@awaitConnection.close()
        }
    }
}

private fun Headers.mapToKtor(): io.ktor.http.Headers = buildHeaders {
    this@mapToKtor.asDynamic().forEach { value: String, key: String ->
        append(key, value)
    }

    Unit
}

/**
 * Wrapper for javascript `error` objects.
 * @property origin: fail reason
 */
class JsError(val origin: dynamic) : Throwable("Error from javascript[$origin].")
