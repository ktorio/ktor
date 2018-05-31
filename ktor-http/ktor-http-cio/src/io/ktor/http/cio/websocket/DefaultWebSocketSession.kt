package io.ktor.http.cio.websocket

import kotlinx.coroutines.experimental.*
import java.time.*

/**
 * Default websocket session with ping-pong and timeout processing and built-in [closeReason] population
 */
interface DefaultWebSocketSession : WebSocketSession {
    var pingInterval: Duration?
    var timeout: Duration
    val closeReason: Deferred<CloseReason?>
}
