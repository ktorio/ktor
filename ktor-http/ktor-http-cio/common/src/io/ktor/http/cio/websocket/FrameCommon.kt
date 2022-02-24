/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*

/**
 * A frame received or ready to be sent. It is not reusable and not thread-safe
 * @property fin is it final fragment, should be always `true` for control frames and if no fragmentation is used
 * @property frameType enum value
 * @property data - a frame content or fragment content
 * @property disposableHandle could be invoked when the frame is processed
 */
public expect sealed class Frame private constructor(
    fin: Boolean,
    frameType: FrameType,
    data: ByteArray,
    disposableHandle: DisposableHandle = NonDisposableHandle,
    rsv1: Boolean = false,
    rsv2: Boolean = false,
    rsv3: Boolean = false
) {
    public val fin: Boolean

    /**
     * First extension bit.
     */
    public val rsv1: Boolean

    /**
     * Second extension bit.
     */
    public val rsv2: Boolean

    /**
     * Third extension bit.
     */
    public val rsv3: Boolean

    public val frameType: FrameType

    public val data: ByteArray

    public val disposableHandle: DisposableHandle

    /**
     * Represents an application level binary frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    public class Binary public constructor(
        fin: Boolean,
        data: ByteArray,
        rsv1: Boolean = false,
        rsv2: Boolean = false,
        rsv3: Boolean = false
    ) : Frame {
        public constructor(fin: Boolean, data: ByteArray)
        public constructor(fin: Boolean, packet: ByteReadPacket)
    }

    /**
     * Represents an application level text frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Please note that a boundary between fragments could be in the middle of multi-byte (unicode) character
     * so don't apply String constructor to every fragment but use decoder loop instead of concatenate fragments first.
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    public class Text public constructor(
        fin: Boolean,
        data: ByteArray,
        rsv1: Boolean = false,
        rsv2: Boolean = false,
        rsv3: Boolean = false,
    ) : Frame {
        public constructor(fin: Boolean, data: ByteArray)
        public constructor(text: String)
        public constructor(fin: Boolean, packet: ByteReadPacket)
    }

    /**
     * Represents a low-level level close frame. It could be sent to indicate web socket session end.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public class Close(data: ByteArray) : Frame {
        public constructor(reason: CloseReason)
        public constructor(packet: ByteReadPacket)
        public constructor()
    }

    /**
     * Represents a low-level ping frame. Could be sent to test connection (peer should reply with [Pong]).
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public class Ping(data: ByteArray) : Frame {
        public constructor(packet: ByteReadPacket)
    }

    /**
     * Represents a low-level pong frame. Should be sent in reply to a [Ping] frame.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    public class Pong(
        data: ByteArray,
        disposableHandle: DisposableHandle = NonDisposableHandle
    ) : Frame {
        public constructor(packet: ByteReadPacket)
    }

    /**
     * Creates a frame copy
     */
    public fun copy(): Frame

    public companion object {
        /**
         * Create a particular [Frame] instance by frame type
         */
        public fun byType(fin: Boolean, frameType: FrameType, data: ByteArray): Frame

        /**
         * Create a particular [Frame] instance by frame type
         */
        public fun byType(
            fin: Boolean,
            frameType: FrameType,
            data: ByteArray,
            rsv1: Boolean,
            rsv2: Boolean,
            rsv3: Boolean
        ): Frame
    }
}

/**
 * Read text content from text frame. Shouldn't be used for fragmented frames: such frames need to be reassembled first
 */
public fun Frame.Text.readText(): String {
    require(fin) { "Text could be only extracted from non-fragmented frame" }
    return Charsets.UTF_8.newDecoder().decode(buildPacket { writeFully(data) })
}

/**
 * Read binary content from a frame. For fragmented frames only returns this fragment.
 */
public fun Frame.readBytes(): ByteArray {
    return data.copyOf()
}

/**
 * Read close reason from close frame or null if no close reason provided
 */
@Suppress("CONFLICTING_OVERLOADS")
public fun Frame.Close.readReason(): CloseReason? {
    if (data.size < 2) {
        return null
    }

    val packet = buildPacket { writeFully(data) }

    val code = packet.readShort()
    val message = packet.readText()

    return CloseReason(code, message)
}

internal object NonDisposableHandle : DisposableHandle {
    override fun dispose() {}
    override fun toString(): String = "NonDisposableHandle"
}
