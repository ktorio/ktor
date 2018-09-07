package io.ktor.http.cio.websocket

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteOrder
import java.nio.*

/**
 * Frame types enum
 * @property controlFrame if this is control frame type
 * @property opcode - frame type id that is used to transport it
 */
enum class FrameType (val controlFrame: Boolean, val opcode: Int) {
    /**
     * Regular application level text frame
     */
    TEXT(false, 1),

    /**
     * Regular application level binary frame
     */
    BINARY(false, 2),

    /**
     * Low level close frame type
     */
    CLOSE(true, 8),

    /**
     * Low level ping frame type
     */
    PING(true, 9),

    /**
     * Low level pong frame type
     */
    PONG(true, 0xa);

    companion object {
        private val maxOpcode = values().maxBy { it.opcode }!!.opcode

        private val byOpcodeArray = Array(maxOpcode + 1) { op -> values().singleOrNull { it.opcode == op } }

        /**
         * Find [FrameType] instance by numeric [opcode]
         * @return a [FrameType] instance or `null` of the [opcode] value is not valid
         */
        operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}

/**
 * A frame received or ready to be sent. It is not reusable and not thread-safe
 * @property fin is it final fragment, should be always `true` for control frames and if no fragmentation is used
 * @property frameType enum value
 * @property buffer - a frame content or fragment content
 * @property disposableHandle could be invoked when the frame is processed
 */
sealed class Frame(val fin: Boolean, val frameType: FrameType, val buffer: ByteBuffer, val disposableHandle: DisposableHandle = NonDisposableHandle) {
    private val initialSize = buffer.remaining()

    /**
     * Represents an application level binary frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    class Binary(fin: Boolean, buffer: ByteBuffer) : Frame(fin, FrameType.BINARY, buffer) {
        constructor(fin: Boolean, packet: ByteReadPacket) : this(fin, packet.readByteBuffer())
    }

    /**
     * Represents an application level text frame.
     * In a RAW web socket session a big text frame could be fragmented
     * (separated into several text frames so they have [fin] = false except the last one).
     * Please note that a boundary between fragments could be in the middle of multi-byte (unicode) character
     * so don't apply String constructor to every fragment but use decoder loop instead of concatenate fragments first.
     * Note that usually there is no need to handle fragments unless you have a RAW web socket session.
     */
    class Text(fin: Boolean, buffer: ByteBuffer) : Frame(fin, FrameType.TEXT, buffer) {
        constructor(text: String) : this(true, ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)))
        constructor(fin: Boolean, packet: ByteReadPacket) : this(fin, packet.readByteBuffer())
    }

    /**
     * Represents a low-level level close frame. It could be sent to indicate web socket session end.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    class Close(buffer: ByteBuffer) : Frame(true, FrameType.CLOSE, buffer) {
        constructor(reason: CloseReason) : this(buildPacket {
            byteOrder = ByteOrder.BIG_ENDIAN
            writeShort(reason.code)
            writeStringUtf8(reason.message)
        })
        constructor(packet: ByteReadPacket) : this(packet.readByteBuffer())
        constructor() : this(Empty)
    }

    /**
     * Represents a low-level ping frame. Could be sent to test connection (peer should reply with [Pong]).
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    class Ping(buffer: ByteBuffer) : Frame(true, FrameType.PING, buffer) {
        constructor(packet: ByteReadPacket) : this(packet.readByteBuffer())
    }

    /**
     * Represents a low-level pong frame. Should be sent in reply to a [Ping] frame.
     * Usually there is no need to send/handle it unless you have a RAW web socket session.
     */
    class Pong(buffer: ByteBuffer, disposableHandle: DisposableHandle) : Frame(true, FrameType.PONG, buffer, disposableHandle) {
        constructor(buffer: ByteBuffer) : this(buffer, NonDisposableHandle)
        constructor(packet: ByteReadPacket) : this(packet.readByteBuffer())
    }

    override fun toString() = "Frame $frameType (fin=$fin, buffer len = $initialSize)"

    /**
     * Creates a frame copy
     */
    fun copy() = byType(fin, frameType, ByteBuffer.allocate(buffer.remaining()).apply { buffer.slice().moveTo(this); clear() })

    companion object {
        private val Empty = ByteBuffer.allocate(0)

        /**
         * Create a particular [Frame] instance by frame type
         */
        fun byType(fin: Boolean, frameType: FrameType, buffer: ByteBuffer): Frame = when (frameType) {
            FrameType.BINARY -> Binary(fin, buffer)
            FrameType.TEXT -> Text(fin, buffer)
            FrameType.CLOSE -> Close(buffer)
            FrameType.PING -> Ping(buffer)
            FrameType.PONG -> Pong(buffer)
        }
    }
}

/**
 * Read text content from text frame. Shouldn't be used for fragmented frames: such frames need to be reassembled first
 */
fun Frame.Text.readText(): String {
    require(fin) { "Text could be only extracted from non-fragmented frame" }
    return Charsets.UTF_8.decode(buffer.duplicate()).toString()
}

/**
 * Read binary content from a frame. For fragmented frames only returns this fragment.
 */
fun Frame.readBytes(): ByteArray {
    return buffer.duplicate().moveToByteArray()
}

/**
 * Read close reason from close frame or null if no close reason provided
 */
fun Frame.Close.readReason(): CloseReason? {
    if (buffer.remaining() < 2) {
        return null
    }

    buffer.mark()
    val code = buffer.getShort()
    val message = buffer.decodeString(Charsets.UTF_8)

    buffer.reset()

    return CloseReason(code, message)
}

private object NonDisposableHandle : DisposableHandle {
    override fun dispose() {}
    override fun toString(): String = "NonDisposableHandle"
}
