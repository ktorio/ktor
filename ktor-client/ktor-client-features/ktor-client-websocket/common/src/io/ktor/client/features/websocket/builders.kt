package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

/**
 * Open [DefaultClientWebSocketSession].
 */
@UseExperimental(WebSocketInternalAPI::class)
suspend fun HttpClient.webSocketSession(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    block: HttpRequestBuilder.() -> Unit = {}
): DefaultClientWebSocketSession = request {
    this.method = method
    url("ws", host, port, path)
    block()
}

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
suspend fun HttpClient.webSocket(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit {
    val session = webSocketSession(method, host, port, path) {
        url.protocol = URLProtocol.WS
        url.port = port
        request()
    }

    try {
        session.block()
    } catch (cause: Throwable) {
        session.close(cause)
        throw cause
    } finally {
        session.close(null)
    }
}

/**
 * Open [DefaultClientWebSocketSession].
 */
suspend fun HttpClient.ws(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(method, host, port, path, request, block)

/**
 * Open secure [DefaultClientWebSocketSession].
 */
suspend fun HttpClient.wss(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(method, host, port, path, request = {
    url.protocol = URLProtocol.WSS
    url.port = port

    request()
}, block = block)
