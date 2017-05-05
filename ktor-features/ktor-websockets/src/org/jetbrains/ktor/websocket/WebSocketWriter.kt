package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketWriter(val writeChannel: WriteChannel) {
    private val serializer = Serializer()
    private val buffer: ByteBuffer = ByteBuffer.allocate(8192)

    private val writeInProgress = AtomicBoolean()

    var masking: Boolean
        get() = serializer.masking
        set(newValue) { serializer.masking = newValue }

    /**
     * enqueue frame for future sending, may block if too many enqueued frames
     */
    fun enqueue(frame: Frame) {
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
                    writeChannel.write(buffer)

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
    }

}