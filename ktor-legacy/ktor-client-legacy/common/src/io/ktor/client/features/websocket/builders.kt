/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.websocket

import io.ktor.http.*
import io.ktor.http.cio.websocket.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("WebSockets(config)", "io.ktor.client.plugins.websocket.*")
)
public fun WebSockets(config: () -> Unit): Unit =
    error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("webSocketSession(block)", "io.ktor.client.plugins.websocket.*")
)
@OptIn(WebSocketInternalAPI::class)
public fun webSocketSession(block: () -> Unit): DefaultClientWebSocketSession =
    error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("webSocketSession(method, host, port, path, block)", "io.ktor.client.plugins.websocket.*")
)
@OptIn(WebSocketInternalAPI::class)
public suspend fun webSocketSession(
    method: HttpMethod = HttpMethod.Get,
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    block: () -> Unit = {}
): DefaultClientWebSocketSession = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("webSocket(request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun webSocket(
    request: () -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith(
        "webSocket(method, host, port, path, request, block)",
        "io.ktor.client.plugins.websocket.*"
    )
)
public suspend fun webSocket(
    method: HttpMethod = HttpMethod.Get,
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")


@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("webSocket(urlString, request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun webSocket(
    urlString: String,
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")


@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ws(method, host, port, path, request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun ws(
    method: HttpMethod = HttpMethod.Get,
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")


@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ws(request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun ws(
    request: () -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ws(urlString, request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun ws(
    urlString: String,
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("wss(request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun wss(
    request: () -> Unit,
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("wss(urlString, request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun wss(
    urlString: String,
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("wss(method, host, port, path, request, block)", "io.ktor.client.plugins.websocket.*")
)
public suspend fun wss(
    method: HttpMethod = HttpMethod.Get,
    host: String = "localhost",
    port: Int = DEFAULT_PORT,
    path: String = "/",
    request: () -> Unit = {},
    block: suspend DefaultClientWebSocketSession.() -> Unit
): Unit = error("Moved to io.ktor.client.plugins.websocket")
