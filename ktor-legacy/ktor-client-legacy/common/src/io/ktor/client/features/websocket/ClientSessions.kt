/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("DEPRECATION_ERROR")

package io.ktor.client.features.websocket

import io.ktor.http.cio.websocket.*

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("ClientWebSocketSession", "io.ktor.client.plugins.websocket.*")
)
public interface ClientWebSocketSession : WebSocketSession

@Deprecated(
    message = "Moved to io.ktor.client.plugins.websocket",
    level = DeprecationLevel.ERROR,
    replaceWith = ReplaceWith("DefaultClientWebSocketSession", "io.ktor.client.plugins.websocket.*")
)
public class DefaultClientWebSocketSession(
    call: Any,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate
