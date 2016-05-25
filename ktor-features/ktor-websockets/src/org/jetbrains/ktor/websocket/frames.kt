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
    class Text(fin: Boolean, buffer: ByteBuffer) : Frame(fin, FrameType.TEXT, buffer)

    class Close(buffer: ByteBuffer) : Frame(true, FrameType.CLOSE, buffer)
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
    return Charsets.UTF_8.decode(buffer).toString()
}
