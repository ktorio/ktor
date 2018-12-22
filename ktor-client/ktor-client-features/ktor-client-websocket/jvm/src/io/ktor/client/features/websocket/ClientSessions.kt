package io.ktor.client.features.websocket

import io.ktor.client.call.*
import io.ktor.http.cio.websocket.*

/**
 * Client specific [WebSocketSession].
 */
interface ClientWebSocketSession : WebSocketSession {
    /**
     * [HttpClientCall] associated with session.
     */
    val call: HttpClientCall
}

/**
 * ClientSpecific [DefaultWebSocketSession].
 */
class DefaultClientWebSocketSession(
    override val call: HttpClientCall,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate {
    override var masking: Boolean = true
}
