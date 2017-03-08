package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

internal class WebSocketWriter(parent: WebSocketImpl, val writeChannel: WriteChannel, val controlFrameHandler: ControlFrameHandler) {
    private val serializer = Serializer(parent::masking)
    private val buffer = ByteBuffer.allocate(8192)

    private val writeInProgress = AtomicBoolean()
    private var closeSent = false

    /**
     * enqueue frame for future sending, may block if too many enqueued frames
     */
    fun enqueue(frame: Frame) {
        if (closeSent) {
            throw IllegalStateException("Outbound is already closed (close frame has been sent)")
        }
        if (frame.frameType == FrameType.CLOSE) {
            closeSent = true
        }

        serializer.enqueue(frame)
    }

    /**
     * Send a frame and write it and all outstanding frames in the queue
     */
    suspend fun send(frame: Frame) {
        enqueue(frame)
        flush()
    }

    /**
     * Ensures all enqueued messages has been written
     */
    suspend fun flush() {
        while (serializer.hasOutstandingBytes) {
            writeLoop()
        }
    }

    private suspend fun writeLoop() {
        if (writeInProgress.compareAndSet(false, true)) {
            try {
                serializer.serialize(buffer)
                buffer.flip()
                while (buffer.hasRemaining()) {
                    if (closeSent && !serializer.hasOutstandingBytes) {
                        controlFrameHandler.closeAfterTimeout()
                    }

                    writeChannel.write(buffer)

                    if (closeSent && !serializer.hasOutstandingBytes) {
                        writeChannel.flush()
                        controlFrameHandler.closeSent()
                        break
                    }

                    buffer.compact()
                    serializer.serialize(buffer)
                    buffer.flip()
                }
                buffer.compact()

                writeChannel.flush()
            } finally {
                writeInProgress.set(false)
            }
        }

        // TODO try {} catch ( parent.closeAsync }
    }

}