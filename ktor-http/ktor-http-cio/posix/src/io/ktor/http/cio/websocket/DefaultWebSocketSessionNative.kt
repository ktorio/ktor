package io.ktor.http.cio.websocket

import kotlinx.coroutines.*

/**
 * Default websocket session with ping-pong and timeout processing and built-in [closeReason] population
 */
actual interface DefaultWebSocketSession : WebSocketSession {
    /**
     * A close reason for this session. It could be `null` if a session is terminated with no close reason
     * (for example due to connection failure).
     */
    actual val closeReason: Deferred<CloseReason?>
}

/**
 * Create [DefaultWebSocketSession] from session.
 */
actual fun DefaultWebSocketSession(
    session: WebSocketSession,
    pingInterval: Long,
    timeoutMillis: Long
): DefaultWebSocketSession = error("There is no CIO native websocket implementation. Consider using platform default.")
