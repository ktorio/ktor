package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.concurrent.*

internal class Serializer {
    private val q = ArrayBlockingQueue<Frame>(1024)

    private var frameBody: ByteBuffer? = null
    private var maskBuffer: ByteBuffer? = null

    @Deprecated("Not yet implemented")
    fun serialize(f: Frame, to: WriteChannel, masking: Boolean): Unit = TODO()

    @Deprecated("")
    var masking = false

    @Deprecated("")
    val hasOutstandingBytes: Boolean
        get() = q.isNotEmpty() || frameBody != null

    @Deprecated("")
    val remainingCapacity: Int get() = q.remainingCapacity()

    @Deprecated("use serialize() instead")
    fun enqueue(f: Frame) {
        q.put(f)
    }

    @Deprecated("Will be rewritten to cio.ByteBufferWriteChannel")
    fun serialize(buffer: ByteBuffer) {
        while (writeCurrentPayload(buffer)) {
            val frame = q.peek() ?: break
            val mask = masking
            setMaskBuffer(mask)

            val headerSize = estimateFrameHeaderSize(frame, mask)
            if (buffer.remaining() < headerSize) {
                break
            }

            serializeHeader(frame, buffer, mask)
            q.remove()
            frameBody = frame.buffer.maskedIfNeeded()
        }
    }

    private fun serializeHeader(f: Frame, buffer: ByteBuffer, mask: Boolean) {
        val size = f.buffer.remaining()
        val length1 = when {
            size < 126 -> size
            size <= Short.MAX_VALUE -> 126
            else -> 127
        }

        buffer.put(
                (f.fin.flagAt(7) or f.frameType.opcode).toByte()
        )
        buffer.put(
                (mask.flagAt(7) or length1).toByte()
        )

        if (length1 == 126) {
            buffer.putShort(f.buffer.remaining().toShort())
        } else if (length1 == 127) {
            buffer.putLong(f.buffer.remaining().toLong())
        }

        maskBuffer?.duplicate()?.putTo(buffer)
    }

    private fun estimateFrameHeaderSize(f: Frame, mask: Boolean): Int {
        val size = f.buffer.remaining()
        return when {
            size < 126 -> 2
            size <= Short.MAX_VALUE -> 2 + 2
            else -> 2 + 8
        } + maskSize(mask)
    }


    private fun writeCurrentPayload(buffer: ByteBuffer): Boolean {
        val frame = frameBody ?: return true
        frame.putTo(buffer)
        if (!frame.hasRemaining()) {
            frameBody = null
            return true
        }

        return false
    }

    private fun maskSize(mask: Boolean) = if (mask) 4 else 0

    private fun ByteBuffer.maskedIfNeeded() = maskBuffer?.let { mask -> copy().apply { xor(mask) } } ?: this

    private fun setMaskBuffer(mask: Boolean) {
        if (mask) {
            maskBuffer = ByteBuffer.allocate(4).apply {
                asIntBuffer().put(nonceRandom.nextInt())
                clear()
            }
        } else {
            maskBuffer = null
        }
    }
}