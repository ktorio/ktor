package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.cio.websocket.*

/**
 * Represents a server-side web socket session
 */
interface WebSocketServerSession : WebSocketSession {
    /**
     * Associated received [call] that originating this session
     */
    val call: ApplicationCall
}

/**
 * Represents a server-side web socket session with all default implementations
 *
 * @see DefaultWebSocketSession
 */
interface DefaultWebSocketServerSession : DefaultWebSocketSession, WebSocketServerSession

/**
 * An application that started this web socket session
 */
val WebSocketServerSession.application: Application get() = call.application

internal fun WebSocketSession.toServerSession(call: ApplicationCall): WebSocketServerSession =
    DelegatedWebSocketServerSession(call, this)

internal fun DefaultWebSocketSession.toServerSession(call: ApplicationCall): DefaultWebSocketServerSession =
    DelegatedDefaultWebSocketServerSession(call, this)

private class DelegatedWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: WebSocketSession
) : WebSocketServerSession, WebSocketSession by delegate

private class DelegatedDefaultWebSocketServerSession(
    override val call: ApplicationCall,
    val delegate: DefaultWebSocketSession
) : DefaultWebSocketServerSession, DefaultWebSocketSession by delegate
