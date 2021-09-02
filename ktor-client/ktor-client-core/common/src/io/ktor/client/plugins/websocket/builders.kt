/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*

/**
 * Install [WebSockets] plugin using the [config] as configuration.
 */
public fun HttpClientConfig<*>.WebSockets(config: WebSockets.Config.() -> Unit) {
    install(WebSockets) {
        config()
    }
}

/**
 * Open [DefaultClientWebSocketSession].
 */
@OptIn(WebSocketInternalAPI::class)
public suspend fun HttpClient.webSocketSession(
    block: HttpRequestBuilder.() -> Unit
): DefaultClientWebSocketSession = request {
    url {
        protocol = URLProtocol.WS
        port = protocol.defaultPort
    }
    block()
}.body()

/**
 * Open [DefaultClientWebSocketSession].
 */
@OptIn(WebSocketInternalAPI::class)
public suspend fun HttpClient.webSocketSession(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): DefaultClientWebSocketSession = webSocketSession {
    this.method = method
    url("ws", host, port, path)
    block()
}

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.webSocket(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
) {
    val session = prepareRequest {
        url {
            protocol = URLProtocol.WS
            port = protocol.defaultPort
        }
        request()
    }

    session.body<DefaultClientWebSocketSession, Unit> {
        try {
            block(it)
        } finally {
            it.close()
        }
    }
}

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.webSocket(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
) {
    webSocket(
        {
            this.method = method
            url("ws", host, port, path)
            request()
        },
        block
    )
}

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.webSocket(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
) {
    webSocket(
        HttpMethod.Get,
        "localhost",
        DEFAULT_PORT,
        "/",
        {
            url.protocol = URLProtocol.WS
            url.port = port

            url.takeFrom(urlString)
            request()
        },
        block
    )
}

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.ws(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(method, host, port, path, request, block)

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.ws(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(request, block)

/**
 * Open [block] with [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.ws(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(urlString, request, block)

/**
 * Open [block] with secure [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.wss(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(
    {
        url.protocol = URLProtocol.WSS
        url.port = url.protocol.defaultPort
        request()
    },
    block = block
)

/**
 * Open [block] with secure [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.wss(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = wss(
    {
        url.takeFrom(urlString)
        request()
    },
    block = block
)

/**
 * Open [block] with secure [DefaultClientWebSocketSession].
 */
public suspend fun HttpClient.wss(
    method: HttpMethod = HttpMethod.Get,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = webSocket(
    method,
    host,
    port,
    path,
    request = {
        url.protocol = URLProtocol.WSS
        if (port != null) url.port = port

        request()
    },
    block = block
)
