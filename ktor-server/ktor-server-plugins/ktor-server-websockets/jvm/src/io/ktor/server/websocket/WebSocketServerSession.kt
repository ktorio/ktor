/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.server.application.*
import io.ktor.shared.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Represents a server-side web socket session
 */
public interface WebSocketServerSession : WebSocketSession {
    /**
     * Associated received [call] that originating this session
     */
    public val call: ApplicationCall
}

/**
 * Represents a server-side web socket session with all default implementations
 *
 * @see DefaultWebSocketSession
 */
public interface DefaultWebSocketServerSession : DefaultWebSocketSession, WebSocketServerSession

/**
 * An application that started this web socket session
 */
public val WebSocketServerSession.application: Application get() = call.application

/**
 *
 */
public suspend inline fun <reified T : Any> WebSocketServerSession.sendSerializedByWebsocketConverter(data: T) {
    val serializedData = application.plugin(WebSockets).contentConverter?.serialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    outgoing.send(Frame.Text(serializedData))
}

/**
 *
 */
public suspend inline fun <reified T: Any> WebSocketServerSession.receiveDeserialized(): T {
    val data = when(val frame = incoming.receive()) {
        is Frame.Text -> frame.data
        is Frame.Binary -> frame.data
        else -> throw WebsocketDeserializeException("Frame type is not Frame.Text or Frame.Binary")
    }

    val result = application.plugin(WebSockets).contentConverter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = ByteReadChannel(data)
    )

    return if (result is T) result
        else throw WebsocketDeserializeException("Can't convert value from json")
}

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
