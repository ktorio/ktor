package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Create raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
suspend fun HttpClient.webSocketRawSession(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    block: HttpRequestBuilder.() -> Unit = {}
): ClientWebSocketSession = request {
    this.method = method
    url("ws", host, port, path)
    block()
}

/**
 * Create raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
suspend fun HttpClient.webSocketRaw(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend ClientWebSocketSession.() -> Unit
): Unit {
    val session = webSocketRawSession(method, host, port, path) {
        url.protocol = URLProtocol.WS
        url.port = port

        request()
    }

    try {
        session.block()
    } catch (cause: Throwable) {
        session.close(cause)
    } finally {
        session.close()
    }
}

/**
 * Create raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
suspend fun HttpClient.wsRaw(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend ClientWebSocketSession.() -> Unit
): Unit = webSocketRaw(method, host, port, path, request, block)


/**
 * Open [DefaultClientWebSocketSession].
 */
suspend fun HttpClient.ws(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit {
    val url = Url(urlString)
    webSocket(HttpMethod.Get, url.host, url.port, url.encodedPath, request, block)
}

/**
 * Create secure raw [ClientWebSocketSession]: no ping-pong and other service messages are used.
 */
suspend fun HttpClient.wssRaw(
    method: HttpMethod = HttpMethod.Get, host: String = "localhost", port: Int = DEFAULT_PORT, path: String = "/",
    request: HttpRequestBuilder.() -> Unit = {}, block: suspend ClientWebSocketSession.() -> Unit
): Unit = webSocketRaw(method, host, port, path, request = {
    url.protocol = URLProtocol.WSS
    url.port = port

    request()
}, block = block)
