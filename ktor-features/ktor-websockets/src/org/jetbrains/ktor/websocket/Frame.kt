package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.util.*
import java.nio.*

enum class FrameType (val controlFrame: Boolean, val opcode: Int) {
    TEXT(false, 1),
    BINARY(false, 2),
    CLOSE(true, 8),
    PING(true, 9),
    PONG(true, 0xa);

    companion object {
        @Deprecated("Use get function instead")
        val byOpcode = values().associateBy { it.opcode }

        private val maxOpcode = values().maxBy { it.opcode }!!.opcode
        private val byOpcodeArray = Array(maxOpcode + 1) { op -> byOpcode[op] }

        operator fun get(opcode: Int): FrameType? = if (opcode in 0..maxOpcode) byOpcodeArray[opcode] else null
    }
}

sealed class Frame(val fin: Boolean, val frameType: FrameType, val buffer: ByteBuffer, val disposableHandle: DisposableHandle = NonDisposableHandle) {
    private val initialSize = buffer.remaining()

    class Binary(fin: Boolean, buffer: ByteBuffer) : Frame(fin, FrameType.BINARY, buffer)
    class Text(fin: Boolean, buffer: ByteBuffer) : Frame(fin, FrameType.TEXT, buffer) {
        constructor(text: String) : this(true, ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8)))
    }

    class Close(buffer: ByteBuffer) : Frame(true, FrameType.CLOSE, buffer) {
        constructor(reason: CloseReason) : this(buildByteBuffer() {
            putShort(reason.code)
            putString(reason.message, Charsets.UTF_8)
        })
        constructor() : this(Empty)
    }
    class Ping(buffer: ByteBuffer) : Frame(true, FrameType.PING, buffer)
    class Pong(buffer: ByteBuffer, disposableHandle: DisposableHandle) : Frame(true, FrameType.PONG, buffer, disposableHandle) {
        constructor(buffer: ByteBuffer) : this(buffer, NonDisposableHandle)
    }

    override fun toString() = "Frame $frameType (fin=$fin, buffer len = $initialSize)"
    fun copy() = byType(fin, frameType, ByteBuffer.allocate(buffer.remaining()).apply { buffer.slice().putTo(this); clear() })

    companion object {
        private val Empty = ByteBuffer.allocate(0)

        fun byType(fin: Boolean, frameType: FrameType, buffer: ByteBuffer): Frame = when (frameType) {
            FrameType.BINARY -> Binary(fin, buffer)
            FrameType.TEXT -> Text(fin, buffer)
            FrameType.CLOSE -> Close(buffer)
            FrameType.PING -> Ping(buffer)
            FrameType.PONG -> Pong(buffer)
        }
    }
}

fun Frame.Text.readText(): String {
    require(fin) { "Text could be only extracted from non-fragmented frame" }
    return Charsets.UTF_8.decode(buffer.duplicate()).toString()
}

fun Frame.Close.readReason(): CloseReason? {
    if (buffer.remaining() < 2) {
        return null
    }

    buffer.mark()
    val code = buffer.getShort()
    val message = buffer.getString(Charsets.UTF_8)

    buffer.reset()

    return CloseReason(code, message)
}
