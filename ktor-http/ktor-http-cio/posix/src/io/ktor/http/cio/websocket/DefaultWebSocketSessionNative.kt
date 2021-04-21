/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.websocket

import io.ktor.util.*
import kotlinx.coroutines.*

/**
 * Default websocket session with ping-pong and timeout processing and built-in [closeReason] population
 */
public actual interface DefaultWebSocketSession : WebSocketSession {
    /**
     * A close reason for this session. It could be `null` if a session is terminated with no close reason
     * (for example due to connection failure).
     */
    public actual val closeReason: Deferred<CloseReason?>

    /**
     * Start WebSocket conversation.
     *
     * @param negotiatedExtensions specify negotiated extensions list to use in current session.
     */
    @OptIn(ExperimentalWebSocketExtensionApi::class)
    @InternalAPI
    public actual fun start(negotiatedExtensions: List<WebSocketExtension<*>>)
}

/**
 * Create [DefaultWebSocketSession] from session.
 */
public actual fun DefaultWebSocketSession(
    session: WebSocketSession,
    pingInterval: Long,
    timeoutMillis: Long
): DefaultWebSocketSession = error("There is no CIO native websocket implementation. Consider using platform default.")
