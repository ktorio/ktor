package io.ktor.http.cio.websocket

import kotlinx.coroutines.*

/**
 * Default websocket session with ping-pong and timeout processing and built-in [closeReason] population
 */
interface DefaultWebSocketSession : WebSocketSession {
    /**
     * Ping interval or `-1L` to disable pinger. Please note that pongs will be handled despite of this setting.
     */
    var pingIntervalMillis: Long

    /**
     * A timeout to wait for pong reply to ping otherwise the session will be terminated immediately.
     * It doesn't have any effect if [pingIntervalMillis] is `-1` (pinger is disabled).
     */
    var timeoutMillis: Long

    /**
     * A close reason for this session. It could be `null` if a session is terminated with no close reason
     * (for example due to connection failure).
     */
    val closeReason: Deferred<CloseReason?>
}
