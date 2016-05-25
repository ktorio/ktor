package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class WebSocketWriter(val parent: WebSocketImpl, val writeChannel: AsyncWriteChannel) : WebSocketOutbound {
    private val buffer = ByteBuffer.allocate(8192)
    private val q = ArrayBlockingQueue<Frame>(1024)
    private var current: Frame? = null
    private val writeInProgress = AtomicBoolean()

    private val listener = object : AsyncHandler {
        override fun success(count: Int) {
            buffer.compact()
            writeInProgress.set(false)
            serializeAndScheduleWrite()
        }

        override fun successEnd() {
        }

        override fun failed(cause: Throwable) {
            parent.close() // TODO
        }
    }

    override fun send(frame: Frame) {
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
        } else {
            buffer.flip()
            writeInProgress.set(false)
        }
    }

    private fun writeCurrentPayload(): Boolean {
        val frame = current ?: return true
        frame.buffer.putTo(buffer)
        if (!frame.buffer.hasRemaining()) {
            current = null
            return true
        }

        return false
    }

    private fun serialize() {
        while (writeCurrentPayload()) {
            val frame = q.peek() ?: break

            val headerSize = estimateFrameHeaderSize(frame)
            if (buffer.remaining() < headerSize) {
                break
            }

            serializeHeader(frame)
            q.remove()
            current = frame
        }
    }

    private fun serializeHeader(f: Frame, mask: Boolean = false) {
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
    }

    private fun estimateFrameHeaderSize(f: Frame): Int {
        val size = f.buffer.remaining()
        return when {
            size < 126 -> 2
            size <= Short.MAX_VALUE -> 2 + 2
            else -> 2 + 8
        } + maskSize()
    }

    private fun maskSize() = 0
}