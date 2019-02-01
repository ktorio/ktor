package io.ktor.client.features.websocket


internal actual fun findWebSocketEngine(): WebSocketEngine = error(
    "Failed to find WebSocketEngine. Consider adding [WebSocketEngine] implementation in dependencies."
)
