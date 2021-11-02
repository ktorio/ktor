/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.cio.websocket.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Client specific [WebSocketSession].
 */
public interface ClientWebSocketSession : WebSocketSession {
    /**
     * [HttpClientCall] associated with session.
     */
    public val call: HttpClientCall
}

/**
 * ClientSpecific [DefaultWebSocketSession].
 */
public class DefaultClientWebSocketSession(
    override val call: HttpClientCall,
    delegate: DefaultWebSocketSession
) : ClientWebSocketSession, DefaultWebSocketSession by delegate

internal class DelegatingClientWebSocketSession(
    override val call: HttpClientCall,
    session: WebSocketSession
) : ClientWebSocketSession, WebSocketSession by session

/**
 * Converter for web socket session
 */
public val DefaultClientWebSocketSession.converter: WebsocketContentConverter?
    get() =
        call.client?.plugin(WebSockets)?.contentConverter

/**
 * Serializes [data] to a frame and enqueues this frame.
 * May suspend if the outgoing queue is full.
 * If the outgoing channel is already closed, throws an exception, so it is impossible to transfer any message.
 * Frames sent after a Close frame are silently ignored.
 * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 */
public suspend inline fun <reified T : Any> DefaultClientWebSocketSession.sendSerialized(data: T) {
    val converter = converter
        ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    val serializedData = converter.serialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        value = data
    )
    outgoing.send(serializedData)
}

/**
 * Dequeues a frame and deserializes it to the type [T] using WebSocket content converter.
 * May throw an exception [WebsocketDeserializeException] if the converter can't deserialize frame data to type [T].
 * May throw [WebsocketDeserializeException] if the received frame type is not [Frame.Text] or [Frame.Binary].
 * In this case, [WebsocketDeserializeException.frame] contains the received frame.
 * May throw [ClosedReceiveChannelException] if a channel was closed
 *
 * @throws WebsocketConverterNotFoundException if no [contentConverter] is found for the [WebSockets] plugin
 * @throws WebsocketDeserializeException if the received frame can't be deserialized to type [T]
 */
public suspend inline fun <reified T : Any> DefaultClientWebSocketSession.receiveDeserialized(): T {
    val converter = converter
        ?: throw WebsocketConverterNotFoundException("No converter was found for websocket")

    val frame = incoming.receive()

    if (!converter.isApplicable(frame)) {
        throw WebsocketDeserializeException(
            "Frame type is ${frame.frameType.name}, expected types: Frame.Text, Frame.Binary",
            frame = frame
        )
    }

    val result = converter.deserialize(
        charset = call.request.headers.suitableCharset(),
        typeInfo = typeInfo<T>(),
        content = frame
    )

    return if (result is T) result
    else throw WebsocketDeserializeException(
        "Can't deserialize value : expected value of type ${T::class.qualifiedName}," +
            " got ${result::class.qualifiedName}",
        frame = frame
    )
}
