package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

internal class WebSocketWriter(val writeChannel: WriteChannel, ctx: CoroutineContext, val pool: ByteBufferPool) {
    private val queue = actor(ctx, capacity = 8) {
        val ticket = pool.allocate(DEFAULT_BUFFER_SIZE)

        try {
            writeLoop(ticket.buffer)
        } finally {
            pool.release(ticket)
        }
    }

    var masking: Boolean = false
    val outgoing: SendChannel<Frame> get() = queue

    fun start(): ActorJob<Frame> {
        return queue.apply { start() }
    }

    suspend fun ActorScope<Any>.writeLoop(buffer: ByteBuffer) {
        val serializer = Serializer()
        buffer.clear()

        for (msg in this) {
            if (msg is Frame) {
                serializer.enqueue(msg)
                if (drainQueueAndSerialize(serializer, buffer, msg is Frame.Close)) break
            } else if (msg is FlushRequest) {
                msg.complete() // we don't need writeChannel.flush() here as we do flush at end of every drainQueueAndSerialize
            } else throw IllegalArgumentException("unknown message $msg")
        }

        close()

        consumeEach { msg ->
            when (msg) {
                is Frame.Close -> {} // ignore
                is Frame.Ping, is Frame.Pong -> {} // ignore
                is FlushRequest -> msg.complete()
                is Frame.Text, is Frame.Binary -> {
                    // TODO log warning?
                }
                else -> throw IllegalArgumentException("unknown message $msg")
            }
        }
    }

    private suspend fun ActorScope<Any>.drainQueueAndSerialize(serializer: Serializer, buffer: ByteBuffer, closed: Boolean): Boolean {
        var flush: FlushRequest? = null
        var closeSent = closed

        while (!isEmpty || serializer.hasOutstandingBytes) {
            while (flush == null && serializer.remainingCapacity > 0) {
                val msg = poll() ?: break
                if (msg is FlushRequest) flush = msg
                else if (msg is Frame.Close) {
                    serializer.enqueue(msg)
                    close()
                    closeSent = true
                    break
                } else if (msg is Frame) {
                    serializer.enqueue(msg)
                } else throw IllegalArgumentException("unknown message $msg")
            }

            serializer.masking = masking
            serializer.serialize(buffer)
            buffer.flip()

            do {
                writeChannel.write(buffer)

                if (!serializer.hasOutstandingBytes && !buffer.hasRemaining()) {
                    flush?.let {
                        writeChannel.flush()
                        it.complete()
                        flush = null
                    }
                }
            } while ((flush != null || closeSent) && buffer.hasRemaining())
            // it is important here to not poll for more frames if we have flush request
            // otherwise flush completion could be delayed for too long while actually could be done

            buffer.compact()

            if (closeSent) break
        }

        // it is important here to flush the channel as some hosts could delay actual bytes transferring
        // as we reached here then we don't have any outstanding messages so we can flush at idle
        writeChannel.flush()

        return closeSent
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
        try {
            val fr = FlushRequest()
            queue.send(fr)

            return suspendCancellableCoroutine { c ->
                fr.setup(c)
            }
        } catch (ifClosed: ClosedSendChannelException) {
            queue.join()
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