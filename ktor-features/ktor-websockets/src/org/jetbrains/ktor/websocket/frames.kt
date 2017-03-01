package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.util.*
import java.nio.*

enum class FrameType (val controlFrame: Boolean, val opcode: Int) {
    TEXT(false, 1),
    BINARY(false, 2),
    CLOSE(true, 8),
    PING(true, 9),
    PONG(true, 0xa);

    companion object {
        val byOpcode = values().associateBy { it.opcode }
    }
}

sealed class Frame(val fin: Boolean, val frameType: FrameType, val buffer: ByteBuffer) {
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
        constructor() : this(ByteBuffer.allocate(0))
    }
    class Ping(buffer: ByteBuffer) : Frame(true, FrameType.PING, buffer)
    class Pong(buffer: ByteBuffer) : Frame(true, FrameType.PONG, buffer)

    override fun toString() = "Frame $frameType (fin=$fin, buffer len = $initialSize)"
    fun copy() = byType(fin, frameType, ByteBuffer.allocate(buffer.remaining()).apply { buffer.slice().putTo(this); clear() })

    companion object {
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
