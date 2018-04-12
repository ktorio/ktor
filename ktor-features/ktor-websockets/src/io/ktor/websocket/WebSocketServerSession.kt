package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.cio.websocket.*

interface WebSocketServerSession : WebSocketSession {
    val call: ApplicationCall
}

interface DefaultWebSocketServerSession : DefaultWebSocketSession, WebSocketServerSession

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
