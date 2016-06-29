package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class WebSocketWriter(val parent: WebSocketImpl, val writeChannel: WriteChannel, val controlFrameHandler: ControlFrameHandler) {
    private val buffer = ByteBuffer.allocate(8192)
    private val q = ArrayBlockingQueue<Frame>(1024)
    private var current: ByteBuffer? = null
    private val writeInProgress = AtomicBoolean()
    private var maskBuffer: ByteBuffer? = null
    private var closeSent = false

    private val listener = object : AsyncHandler {
        override fun success(count: Int) {
            if (closeSent) {
                controlFrameHandler.closeSent()
            }
            buffer.compact()
            writeInProgress.set(false)
            serializeAndScheduleWrite()
        }

        override fun successEnd() {
        }

        override fun failed(cause: Throwable) {
            parent.closeAsync(null)
        }
    }

    fun send(frame: Frame) {
        if (closeSent) {
            throw IllegalStateException("Outbound is already closed (close frame has been sent)")
        }
        if (frame.frameType == FrameType.CLOSE) {
            closeSent = true
        }

        q.put(frame)
        serializeAndScheduleWrite()
    }

    private fun serializeAndScheduleWrite() {
        if (writeInProgress.compareAndSet(false, true)) {
            serialize()
            doSend()
        }
    }

    private fun doSend() {
        buffer.flip()
        if (buffer.hasRemaining()) {
            writeChannel.write(buffer, listener)
            writeChannel.requestFlush()
        } else {
            buffer.compact()
            writeInProgress.set(false)
        }
    }

    private fun writeCurrentPayload(): Boolean {
        val frame = current ?: return true
        frame.putTo(buffer)
        if (!frame.hasRemaining()) {
            current = null
            return true
        }

        return false
    }

    private fun serialize() {
        while (writeCurrentPayload()) {
            val frame = q.peek() ?: break
            val mask = parent.masking
            setMaskBuffer(mask)

            val headerSize = estimateFrameHeaderSize(frame, mask)
            if (buffer.remaining() < headerSize) {
                break
            }

            serializeHeader(frame, mask)
            q.remove()
            current = frame.buffer.masked()
        }
    }

    private fun serializeHeader(f: Frame, mask: Boolean) {
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

        maskBuffer?.let { bb ->
            bb.duplicate().putTo(buffer)
        }
    }

    private fun estimateFrameHeaderSize(f: Frame, mask: Boolean): Int {
        val size = f.buffer.remaining()
        return when {
            size < 126 -> 2
            size <= Short.MAX_VALUE -> 2 + 2
            else -> 2 + 8
        } + maskSize(mask)
    }

    private fun maskSize(mask: Boolean) = if (mask) 4 else 0

    private fun ByteBuffer.masked() = maskBuffer?.let { mask -> copy().apply { xor(mask) } } ?: this

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