package io.ktor.websocket

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import java.nio.ByteBuffer
import kotlin.coroutines.experimental.*

internal class WebSocketWriter(val writeChannel: ByteWriteChannel, val parent: Job, ctx: CoroutineContext, val pool: ByteBufferPool) {
    private val queue = actor(ctx + parent, capacity = 8, start = CoroutineStart.LAZY) {
        val ticket = pool.allocate(DEFAULT_BUFFER_SIZE)
        try {
            writeLoop(ticket.buffer)
        } finally {
            pool.release(ticket)
        }
    }

    var masking: Boolean = false
    val outgoing: SendChannel<Frame> get() = queue
    val serializer = Serializer()

    private suspend fun ActorScope<Any>.writeLoop(buffer: ByteBuffer) {
        buffer.clear()
        try {
            loop@ for (msg in this) {
                when (msg) {
                    is Frame -> if (drainQueueAndSerialize(msg, buffer)) break@loop
                    is FlushRequest -> msg.complete() // we don't need writeChannel.flush() here as we do flush at end of every drainQueueAndSerialize
                    else -> throw IllegalArgumentException("unknown message $msg")
                }
            }
        }
        finally {
            close()
            writeChannel.close()
        }

        consumeEach { msg ->
            when (msg) {
                is Frame.Close -> {} // ignore
                is Frame.Ping, is Frame.Pong -> {} // ignore
                is FlushRequest -> msg.complete()
                is Frame.Text, is Frame.Binary -> {
                    // discard
                }
                else -> throw IllegalArgumentException("unknown message $msg")
            }
        }
    }

    private suspend fun ActorScope<Any>.drainQueueAndSerialize(firstMsg: Frame, buffer: ByteBuffer): Boolean {
        var flush: FlushRequest? = null
        serializer.enqueue(firstMsg)
        var closeSent = firstMsg is Frame.Close

        // initially serializer has at least one message queued
        while (true) {
            while (flush == null && !closeSent && serializer.remainingCapacity > 0) {
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

            if (!serializer.hasOutstandingBytes && buffer.position() == 0) break

            serializer.masking = masking
            serializer.serialize(buffer)
            buffer.flip()

            do {
                writeChannel.writeFully(buffer)

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
        }

        // it is important here to flush the channel as some engines could delay actual bytes transferring
        // as we reached here then we don't have any outstanding messages so we can flush at idle
        writeChannel.flush()
        flush?.complete()

        return closeSent
    }

    /**
     * Send a frame and write it and all outstanding frames in the queue
     */
    suspend fun send(frame: Frame) = queue.send(frame)

    /**
     * Ensures all enqueued messages has been written
     */
    suspend fun flush() = FlushRequest(parent).also { queue.send(it) }.await()

    fun close() {
        queue.close()
    }

    private class FlushRequest(parent: Job) {
        private val done = CompletableDeferred<Unit>()
        init {
            val reg = parent.attachChild(done)
            done.invokeOnCompletion {
                reg.dispose()
            }
        }

        fun complete() = done.complete(Unit)
        suspend fun await() = done.await()
    }
}