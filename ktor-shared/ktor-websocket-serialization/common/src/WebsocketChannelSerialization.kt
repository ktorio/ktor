import io.ktor.http.cio.websocket.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.channels.*

/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

public object WebsocketChannelSerialization {
    /**
     * Serializes [data] to a frame and enqueues this frame.
     * May suspend if the [outgoing] queue is full.
     * If the [outgoing] channel is already closed, throws an exception, so it is impossible to transfer any message.
     * Frames sent after a Close frame are silently ignored.
     * Note that a Close frame could be sent automatically in reply to a peer's Close frame unless it is a raw WebSocket session.
     *
     * @param data The data to serialize
     * @param converter The WebSocket converter
     * @param charset Response charset
     * @param outgoing An outgoing WebSocket send channel
     */
    public suspend inline fun <reified T : Any> sendSerialized(
        data: T,
        converter : WebsocketContentConverter,
        charset: Charset,
        outgoing: SendChannel<Frame>
    ) {
        val serializedData = converter.serialize(
            charset = charset,
            typeInfo = typeInfo<T>(),
            value = data
        )
        outgoing.send(serializedData)
    }

    /**
     * Dequeues a frame and deserializes it to the type [T] using [converter].
     * May throw [WebsocketDeserializeException] if the received frame type is not [Frame.Text] or [Frame.Binary].
     * In this case, [WebsocketDeserializeException.frame] contains the received frame.
     * May throw [ClosedReceiveChannelException] if a channel was closed
     *
     * @param converter The WebSocket converter
     * @param charset Response charset
     * @param incoming An incoming WebSocket receive channel
     *
     * @returns A deserialized value or throws [WebsocketDeserializeException] if the [converter]
     * can't deserialize frame data to type [T]
     */
    public suspend inline fun <reified T : Any> receiveDeserialized(
        converter : WebsocketContentConverter,
        charset: Charset,
        incoming: ReceiveChannel<Frame>
    ): T {
        val frame = incoming.receive()

        if (!converter.isApplicable(frame)) {
            throw WebsocketDeserializeException(
                "Frame type is ${frame.frameType.name}, expected types: Frame.Text, Frame.Binary",
                frame = frame
            )
        }

        val result = converter.deserialize(
            charset = charset,
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
}
