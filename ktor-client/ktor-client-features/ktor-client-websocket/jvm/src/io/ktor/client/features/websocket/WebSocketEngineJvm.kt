package io.ktor.client.features.websocket

internal actual fun findWebSocketEngine(): WebSocketEngine = error(
    "Failed to find WebSocket client engine implementation in the classpath: consider adding WebSocketEngine dependency."
)
