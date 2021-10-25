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
 * Serializes [data] of type [T] to frame using websocket content converter and enqueue this frame,
 * may suspend if outgoing queue is full.
 * May throw an exception if outgoing channel is already closed, so it is impossible to transfer any message.
 * Frames that were sent after close frame could be silently ignored.
 * Please note that close frame could be sent automatically in reply to a peer close frame unless it is
 * raw websocket session.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 */
public suspend inline fun <reified T : Any> WebSocketServerSession.sendSerializedByWebsocketConverter(data: T) {
    val charset = call.request.headers.suitableCharset()
    val serializedData = application.plugin(WebSockets).contentConverter?.serialize(
        charset = charset,
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    outgoing.send(
        Frame.Text(
            String(
                serializedData.toByteArray(),
                charset = charset
            )
        )
    )
}

/**
 * Dequeue frame and deserializes to type [T] using websocket content converter.
 * Please note that you don't need to use this method with raw websocket session
 * or if you're expecting ping, pong, close frames.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 * @throws WebsocketDeserializeException if received frame can't be deserialized to type [T]
 */
public suspend inline fun <reified T : Any> WebSocketServerSession.receiveDeserialized(): T {
    val data = when (val frame = incoming.receive()) {
        is Frame.Text -> frame.data
        is Frame.Binary -> frame.data
        else -> throw WebsocketDeserializeException(
            "Frame type is not Frame.Text or Frame.Binary or websocket was closed"
        )
    }

    val result = application.plugin(WebSockets).contentConverter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = ByteReadChannel(data)
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

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
