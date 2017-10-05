package io.ktor.websocket

import kotlinx.coroutines.experimental.*
import java.time.*

interface DefaultWebSocketSession : WebSocketSession {
    var pingInterval: Duration?
    var timeout: Duration
    val closeReason: Deferred<CloseReason?>
}