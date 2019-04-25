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
import org.w3c.fetch.Headers
import kotlin.coroutines.*

internal class JsClientEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Default

    override val coroutineContext: CoroutineContext = dispatcher + SupervisorJob()

    override suspend fun execute(
        data: HttpRequestData
    ): HttpResponseData {
        val callContext: CoroutineContext = Job(this@JsClientEngine.coroutineContext[Job]) + dispatcher

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

        socket.awaitConnection()

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

    override fun close() {}
}

private suspend fun WebSocket.awaitConnection(): WebSocket = suspendCancellableCoroutine { continuation ->
    onopen = { continuation.resume(this) }
    onerror = { continuation.resumeWithException(WebSocketException("$it")) }
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
