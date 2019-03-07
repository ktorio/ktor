package io.ktor.client.features.websocket

import io.ktor.client.request.*
import kotlinx.coroutines.*

internal expect fun findWebSocketEngine(): WebSocketEngine

/**
 * Client engine implementing WebSocket protocol.
 * RFC: https://tools.ietf.org/html/rfc6455
 */
interface WebSocketEngine : CoroutineScope {
    /**
     * Execute WebSocket protocol [request].
     */
    suspend fun execute(request: HttpRequest): WebSocketResponse
}
