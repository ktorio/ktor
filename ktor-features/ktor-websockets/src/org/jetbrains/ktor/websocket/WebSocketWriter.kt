package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.Channel
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*

internal class WebSocketWriter(val writeChannel: WriteChannel) {
    val queue = Channel<Any>(1024)
    var masking: Boolean = false

    fun start(ctx: CoroutineDispatcher, pool: ByteBufferPool): Job {
        return launch(ctx) {
            val ticket = pool.allocate(DEFAULT_BUFFER_SIZE)

            try {
                writeLoop(ticket.buffer)
            } finally {
                pool.release(ticket)
            }
        }
    }

    suspend fun writeLoop(buffer: ByteBuffer) {
        val serializer = Serializer()
        buffer.clear()

        while (true) {
            val msg = queue.receiveOrNull() ?: break

            if (msg is Frame) {
                serializer.enqueue(msg)
                drainQueueAndSerialize(serializer, buffer)
            } else if (msg is FlushRequest) {
                writeChannel.flush()
                msg.complete()
            } else throw IllegalArgumentException("unknown message $msg")
        }
    }

    private suspend fun drainQueueAndSerialize(serializer: Serializer, buffer: ByteBuffer) {
        var flush: FlushRequest? = null

        while (!queue.isEmpty || serializer.hasOutstandingBytes) {
            while (flush == null && serializer.remainingCapacity > 0) {
                val msg = queue.poll() ?: break
                if (msg is FlushRequest) flush = msg
                else if (msg is Frame) {
                    serializer.enqueue(msg)
                }
                else throw IllegalArgumentException("unknown message $msg")
            }

            serializer.masking = masking
            serializer.serialize(buffer)
            buffer.flip()
            writeChannel.write(buffer)

            if (!serializer.hasOutstandingBytes && !buffer.hasRemaining()) {
                flush?.let {
                    writeChannel.flush()
                    it.complete()
                    flush = null
                }
            }

            buffer.compact()
        }

        // it is important here to flush the channel as some hosts could delay actual bytes transferring
        // as we reached here then we don't have any outstanding messages so we can flush at idle
        writeChannel.flush()
    }


    /**
     * Send a frame and write it and all outstanding frames in the queue
     */
    suspend fun send(frame: Frame) {
        queue.send(frame)
    }

    /**
     * Ensures all enqueued messages has been written
     */
    suspend fun flush() {
        val fr = FlushRequest()
        queue.send(fr)

        return suspendCancellableCoroutine { c ->
            fr.setup(c)
        }
    }

    fun close() {
        queue.close()
    }

    private class FlushRequest {
        private val flushed = AtomicBoolean()
        private val continuation = AtomicReference<CancellableContinuation<Unit>?>()

        fun complete() {
            if (flushed.compareAndSet(false, true)) {
                continuation.getAndSet(null)?.resume(Unit)
            }
        }

        fun setup(c: CancellableContinuation<Unit>) {
            if (flushed.get()) {
                c.resume(Unit)
            } else {
                if (!continuation.compareAndSet(null, c)) throw IllegalStateException()
                if (flushed.get()) {
                    continuation.compareAndSet(c, null)
                    c.resume(Unit)
                }
            }
        }
    }
}