package io.ktor.client.features.websocket

import io.ktor.client.call.*
import io.ktor.http.cio.websocket.*

interface ClientWebSocketSession : WebSocketSession {
    val call: HttpClientCall
}

class DefaultClientWebSocketSession(
    override val call: HttpClientCall,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate {
    override var masking: Boolean = true
}
