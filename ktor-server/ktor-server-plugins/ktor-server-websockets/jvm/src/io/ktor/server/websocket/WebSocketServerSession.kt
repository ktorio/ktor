/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.serialization.*
import io.ktor.server.application.*
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
 * Converter for web socket session
 */
public val WebSocketServerSession.converter: WebsocketContentConverter? get() =
    application.plugin(WebSockets).contentConverter

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
public suspend inline fun <reified T : Any> WebSocketServerSession.sendSerialized(data: T) {
    val serializedData = converter?.serialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        value = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    outgoing.send(serializedData)
}

/**
 * Dequeue frame and deserializes to type [T] using websocket content converter.
 * May throw an exception [WebsocketDeserializeException] if converter can't deserialize frame data to type [T].
 * May throw [WebsocketDeserializeException] if received frame type is not [Frame.Text] or [Frame.Binary].
 * In this case [WebsocketDeserializeException.frame] contains received frame.
 *
 * May throw [ClosedReceiveChannelException] if channel was closed
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 * @throws WebsocketDeserializeException if received frame can't be deserialized to type [T]
 */
public suspend inline fun <reified T : Any> WebSocketServerSession.receiveDeserialized(): T {
    val frame = incoming.receive()
    val data = when (frame) {
        is Frame.Text -> frame
        is Frame.Binary -> frame
        else -> throw WebsocketDeserializeException(
            "Frame type is ${frame.frameType.name}, expected types: Frame.Text, Frame.Binary",
            frame = frame
        )
    }

    val result = converter?.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = data
    ) ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    return if (result is T) result
    else throw WebsocketDeserializeException(
        "Can't deserialize value : expected value of type ${T::class.qualifiedName}," +
            " got ${result::class.qualifiedName}",
        frame = frame
    )
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
