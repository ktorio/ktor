package io.ktor.http.cio.websocket

import io.ktor.cio.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
import kotlin.coroutines.experimental.*

/**
 * Class that processes written [outgoing] Websocket [Frame],
 * serializes them and writes the bits into the [writeChannel].
 */
class WebSocketWriter @Deprecated("Internal API") constructor(
    private val writeChannel: ByteWriteChannel,
    private val parent: Job,
    context: CoroutineContext,
    /**
     * Whether it will mask serialized frames.
     */
    var masking: Boolean = false,
    val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) {
    private val queue = actor(context, parent = parent, capacity = 8, start = CoroutineStart.LAZY) {
        pool.use { writeLoop(it) }
    }

    /**
     * Channel for sending Websocket's [Frame] that will be serialized and written to [writeChannel].
     */
    val outgoing: SendChannel<Frame> get() = queue

    private val serializer = @Suppress("DEPRECATION") Serializer()

    private suspend fun ActorScope<Any>.writeLoop(buffer: ByteBuffer) {
        buffer.clear()
        try {
            loop@ for (message in this) {
                when (message) {
                    is Frame -> if (drainQueueAndSerialize(message, buffer)) break@loop
                    is FlushRequest -> message.complete() // we don't need writeChannel.flush() here as we do flush at end of every drainQueueAndSerialize
                    else -> throw IllegalArgumentException("unknown message $message")
                }
            }
        } finally {
            close()
            writeChannel.close()
        }

        consumeEach { message ->
            when (message) {
                is Frame.Close -> {} // ignore
                is Frame.Ping, is Frame.Pong -> {} // ignore
                is FlushRequest -> message.complete()
                is Frame.Text, is Frame.Binary -> {} // discard
                else -> throw IllegalArgumentException("unknown message $message")
            }
        }
    }

    private suspend fun ActorScope<Any>.drainQueueAndSerialize(firstMsg: Frame, buffer: ByteBuffer): Boolean {
        var flush: FlushRequest? = null
        serializer.enqueue(firstMsg)
        var closeSent = firstMsg is Frame.Close

        // initially serializer has at least one message queued
        while (true) {
            poll@ while (flush == null && !closeSent && serializer.remainingCapacity > 0) {
                val message = poll() ?: break
                when (message) {
                    is FlushRequest -> flush = message
                    is Frame.Close -> {
                        serializer.enqueue(message)
                        close()
                        closeSent = true
                        break@poll
                    }
                    is Frame -> serializer.enqueue(message)
                    else -> throw IllegalArgumentException("unknown message $message")
                }
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

    /**
     * Closes the message queue
     */
    fun close() {
        queue.close()
    }

    private class FlushRequest(parent: Job) {
        private val done = CompletableDeferred<Unit>(parent)
        fun complete() = done.complete(Unit)
        suspend fun await() = done.await()
    }
}
