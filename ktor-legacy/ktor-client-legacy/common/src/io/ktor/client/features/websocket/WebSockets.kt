/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.websocket

import io.ktor.client.features.*
import io.ktor.http.cio.websocket.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("WebSocketCapability", "io.ktor.client.plugins.websocket.*")
)
public object WebSocketCapability

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("WebSocketExtensionsCapability", "io.ktor.client.plugins.websocket.*")
)
public object WebSocketExtensionsCapability

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("WebSockets", "io.ktor.client.plugins.websocket.*")
)
@OptIn(WebSocketInternalAPI::class)
public class WebSockets

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("WebSocketException", "io.ktor.client.plugins.websocket.*")
)
public class WebSocketException(message: String) : IllegalStateException(message)
