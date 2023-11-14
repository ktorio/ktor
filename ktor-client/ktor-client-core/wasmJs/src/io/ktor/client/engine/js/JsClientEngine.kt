/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.js

import io.ktor.client.engine.*
import io.ktor.client.engine.js.compatibility.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.coroutines.*

@Suppress("UNUSED_PARAMETER")
private fun createBrowserWebSocket(urlString_capturingHack: String): WebSocket =
    js("new WebSocket(urlString_capturingHack)")

@Suppress("UNUSED_PARAMETER")
private fun createWebSocketNodeJs(
    socketCtor: JsAny,
    urlString_capturingHack: String,
    headers_capturingHack: JsAny
): WebSocket =
    js("new socketCtor(urlString_capturingHack, { headers: headers_capturingHack })")

internal class JsClientEngine(
    override val config: HttpClientEngineConfig
) : HttpClientEngineBase("ktor-js") {

    override val supportedCapabilities = setOf(HttpTimeoutCapability, WebSocketCapability, SSECapability)

    init {
        check(config.proxy == null) { "Proxy unsupported in Js engine." }
    }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val clientConfig = data.attributes[CLIENT_CONFIG]

        if (data.isUpgradeRequest()) {
            return executeWebSocketRequest(data, callContext)
        }

        val requestTime = GMTDate()
        val rawRequest = data.toRaw(clientConfig, callContext)

        val rawResponse = commonFetch(data.url.toString(), rawRequest)
        val status = HttpStatusCode(rawResponse.status.toInt(), rawResponse.statusText)
        val headers = rawResponse.headers.mapToKtor()
        val version = HttpProtocolVersion.HTTP_1_1

        val body = CoroutineScope(callContext).readBody(rawResponse)
        val responseBody: Any = if (data.isSseRequest()) {
            DefaultClientSSESession(data.body as SSEClientContent, body, callContext, status, headers)
        } else {
            body
        }

        return HttpResponseData(
            status,
            requestTime,
            headers,
            version,
            responseBody,
            callContext
        )
    }

    // Adding "_capturingHack" to reduce chances of JS IR backend to rename variable,
    // so it can be accessed inside js("") function
    @Suppress("UNUSED_PARAMETER", "UnsafeCastFromDynamic", "UNUSED_VARIABLE", "LocalVariableName")
    private fun createWebSocket(
        urlString_capturingHack: String,
        headers: Headers
    ): WebSocket = when (PlatformUtils.platform) {
        Platform.Browser -> createBrowserWebSocket(urlString_capturingHack)
        else -> {
            val ws_capturingHack = makeRequire<JsAny>("ws")
            val headers_capturingHack = makeJsObject<JsAny>()
            headers.forEach { name, values ->
                headers_capturingHack[name] = values.joinToString(",")
            }
            createWebSocketNodeJs(ws_capturingHack, urlString_capturingHack, headers_capturingHack)
        }
    }

    private suspend fun executeWebSocketRequest(
        request: HttpRequestData,
        callContext: CoroutineContext
    ): HttpResponseData {
        val requestTime = GMTDate()
        val urlString = request.url.toString()
        val socket: WebSocket = createWebSocket(urlString, request.headers)

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
            Headers.Empty,
            HttpProtocolVersion.HTTP_1_1,
            session,
            callContext
        )
    }
}

public actual typealias CommonEventListener = EventListener
public actual typealias CommonEvent = Event
public actual typealias CommonEventTarget = EventTarget
public actual typealias CommonWebSocket = WebSocket

internal actual fun addEventListener(target: CommonWebSocket, type: String, callback: ((Event) -> Unit)?) {
    target.addEventListener(type, callback)
}

internal actual fun removeEventListener(target: CommonWebSocket, type: String, callback: ((CommonEvent) -> Unit)?) {
    target.removeEventListener(type, callback)
}

internal actual fun close(socket: WebSocket): Unit = socket.close()

internal actual fun eventAsString(event: Event): String =
    js("JSON.stringify(event, ['message', 'target', 'type', 'isTrusted'])")

private fun getKeys(headers: org.w3c.fetch.Headers): JsArray<JsString> =
    js("Array.from(headers.keys())")

internal fun org.w3c.fetch.Headers.mapToKtor(): Headers = buildHeaders {
    val keys = getKeys(this@mapToKtor)
    for (i in 0 until keys.length) {
        val key = keys[i].toString()
        val value = this@mapToKtor.get(key)!!
        append(key, value)
    }
}

/**
 * Wrapper for javascript `error` objects.
 * @property origin: fail reason
 */
@Suppress("MemberVisibilityCanBePrivate")
public class JsError(public val origin: JsAny) : Throwable("Error from javascript[$origin].")
