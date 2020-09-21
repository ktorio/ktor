/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio.websocket

import kotlinx.coroutines.*
import io.ktor.utils.io.core.*

/**
 * A frame received or ready to be sent. It is not reusable and not thread-safe
 * @property fin is it final fragment, should be always `true` for control frames and if no fragmentation is used
 * @property frameType enum value
 * @property data - a frame content or fragment content
 * @property disposableHandle could be invoked when the frame is processed
 */
public actual sealed class Frame actual constructor(
    public actual val fin: Boolean,
    public actual val frameType: FrameType,
    public actual val data: ByteArray,
    public actual val disposableHandle: DisposableHandle
) {
    /**
     * Represents an application level binary frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    public actual class Binary actual constructor(fin: Boolean, data: ByteArray) : Frame(fin, FrameType.BINARY, data, NonDisposableHandle) {
        public actual constructor(fin: Boolean, packet: ByteReadPacket) : this(fin, packet.readBytes())
    }

    /**
     * Represents an application level text frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Please note that a boundary between fragments could be in the middle of multi-byte (unicode) character
     * so don't apply String constructor to every fragment but use decoder loop instead of concatenate fragments first.
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    public actual class Text actual constructor(fin: Boolean, data: ByteArray) : Frame(fin, FrameType.TEXT, data, NonDisposableHandle) {
        public actual constructor(text: String) : this(true, text.toByteArray())
        public actual constructor(fin: Boolean, packet: ByteReadPacket) : this(fin, packet.readBytes())
    }

    /**
     * Represents a low-level level close frame. It could be sent to indicate web socket session end.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public actual class Close actual constructor(data: ByteArray) : Frame(true, FrameType.CLOSE, data, NonDisposableHandle) {
        public actual constructor(reason: CloseReason) : this(buildPacket {
            writeShort(reason.code)
            writeText(reason.message)
        })

        public actual constructor(packet: ByteReadPacket) : this(packet.readBytes())
        public actual constructor() : this(Empty)
    }

    /**
     * Represents a low-level ping frame. Could be sent to test connection (peer should reply with [Pong]).
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public actual class Ping actual constructor(data: ByteArray) : Frame(true, FrameType.PING, data, NonDisposableHandle) {
        public actual constructor(packet: ByteReadPacket) : this(packet.readBytes())
    }

    /**
     * Represents a low-level pong frame. Should be sent in reply to a [Ping] frame.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public actual class Pong actual constructor(
        data: ByteArray,
        disposableHandle: DisposableHandle
    ) : Frame(true, FrameType.PONG, data, disposableHandle) {
        public actual constructor(packet: ByteReadPacket) : this(packet.readBytes(), NonDisposableHandle)
    }

    override fun toString(): String = "Frame $frameType (fin=$fin, buffer len = ${data.size})"

    /**
     * Creates a frame copy
     */
    public actual fun copy(): Frame = byType(fin, frameType, data.copyOf())

    public actual companion object {
        private val Empty: ByteArray = ByteArray(0)

        /**
         * Create a particular [Frame] instance by frame type
         */
        public actual fun byType(
            fin: Boolean,
            frameType: FrameType,
            data: ByteArray
        ): Frame = when (frameType) {
            FrameType.BINARY -> Binary(fin, data)
            FrameType.TEXT -> Text(fin, data)
            FrameType.CLOSE -> Close(data)
            FrameType.PING -> Ping(data)
            FrameType.PONG -> Pong(data, NonDisposableHandle)
        }

    }
}
