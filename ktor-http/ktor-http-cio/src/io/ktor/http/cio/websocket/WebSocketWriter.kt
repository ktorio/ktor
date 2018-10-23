package io.ktor.http.cio.websocket

import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.*
import kotlinx.io.pool.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Class that processes written [outgoing] Websocket [Frame],
 * serializes them and writes the bits into the [writeChannel].
 */
@WebSocketInternalAPI
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
class WebSocketWriter(
        private val writeChannel: ByteWriteChannel,
        override val coroutineContext: CoroutineContext,
        /**
         * Whether it will mask serialized frames.
         */
        var masking: Boolean = false,
        /**
         * ByteBuffer pool to be used by this writer
         */
        val pool: ObjectPool<ByteBuffer> = KtorDefaultPool
) : CoroutineScope {

    @Deprecated("Specify parent through coroutineContext",
            replaceWith = ReplaceWith("WebSocketWriter(writeChannel, coroutineContext, masking, pool)"))
    constructor(writeChannel: ByteWriteChannel,
                parent: Job?,
                coroutineContext: CoroutineContext,
                masking: Boolean, pool: ObjectPool<ByteBuffer> = KtorDefaultPool) : this(writeChannel, coroutineContext + (parent
            ?: Dispatchers.Unconfined), masking, pool)

    @Suppress("RemoveExplicitTypeArguments") // workaround for new kotlin inference issue
    private val queue = actor<Any>(capacity = 8, start = CoroutineStart.LAZY) {
        pool.useInstance { it: ByteBuffer -> writeLoop(it) }
    }

    /**
     * Channel for sending Websocket's [Frame] that will be serialized and written to [writeChannel].
     */
    val outgoing: SendChannel<Frame> get() = queue

    private val serializer = Serializer()

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
                is Frame.Close -> {
                } // ignore
                is Frame.Ping, is Frame.Pong -> {
                } // ignore
                is FlushRequest -> message.complete()
                is Frame.Text, is Frame.Binary -> {
                } // discard
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
    suspend fun send(frame: Frame): Unit = queue.send(frame)

    /**
     * Ensures all enqueued messages has been written
     */
    suspend fun flush(): Unit = FlushRequest(coroutineContext[Job]).also {
        try {
            queue.send(it)
        } catch (sendFailure: Throwable) {
            it.complete()
            throw sendFailure
        }
    }.await()

    /**
     * Closes the message queue
     */
    fun close() {
        queue.close()
    }

    private class FlushRequest(parent: Job?) {
        private val done = CompletableDeferred<Unit>(parent)
        fun complete() = done.complete(Unit)
        suspend fun await() = done.await()
    }
}
