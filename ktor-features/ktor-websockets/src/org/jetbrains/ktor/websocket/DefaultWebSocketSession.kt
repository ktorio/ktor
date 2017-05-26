package org.jetbrains.ktor.websocket

import java.time.*

interface DefaultWebSocketSession : WebSocketSession {
    var pingInterval: Duration?
    var timeout: Duration
    val closeReason: CloseReason?
}