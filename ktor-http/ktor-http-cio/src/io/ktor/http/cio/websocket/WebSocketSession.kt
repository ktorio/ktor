package io.ktor.http.cio.websocket

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.*
import kotlin.coroutines.*

/**
 * Represents a web socket session between two peers
 */
interface WebSocketSession : CoroutineScope {
    /**
     * Incoming frames channel
     */
    val incoming: ReceiveChannel<Frame>

    /**
     * Outgoing frames channel. It could have limited capacity so sending too much frames may lead to suspension at
     * corresponding send invocations. It also may suspend if a peer doesn't read frames for some reason.
     */
    val outgoing: SendChannel<Frame>

    /**
     * Enable or disable masking output messages by a random xor mask.
     * Please note that changing this flag on the fly could be applied to the messages already sent (enqueued earlier)
     * as the sending pipeline works asynchronously
     */
    var masking: Boolean

    /**
     * Specifies frame size limit. Connection will be closed if violated
     */
    var maxFrameSize: Long

    /**
     * Dispatcher to handle io operations
     */
    @Deprecated("Use coroutineContext instead", ReplaceWith("coroutineContext"))
    val dispatcher: CoroutineContext get() = coroutineContext

    /**
     * Flush all outstanding messages and suspend until all earlier sent messages will be written. Could be called
     * at any time even after close. May return immediately if the connection is already terminated.
     * However it may also fail with an exception (or cancellation) at any point due to session failure.
     * Please note that [flush] doesn't guarantee that frames were actually delivered.
     */
    suspend fun flush()

    /**
     * Enqueue frame, may suspend if outgoing queue is full. May throw an exception if outgoing channel is already
     * closed so it is impossible to transfer any message. Frames that were sent after close frame could be silently
     * ignored. Please note that close frame could be sent automatically in reply to a peer close frame unless it is
     * raw websocket session.
     */
    suspend fun send(frame: Frame) {
        outgoing.send(frame)
    }

    /**
     * Initiate connection termination immediately. Termination may complete asynchronously.
     */
    fun terminate()

    /**
     * Close session with the specified [cause] or with no reason if `null`
     */
    @KtorExperimentalAPI
    suspend fun close(cause: Throwable? = null)
}

/**
 * Send a close frame with the specified [reason]. May suspend if outgoing channel is full or
 * may throw an exception if it is already closed. The specified [reason] could be ignored if there was already
 * close frame sent (for example in reply to a peer close frame).
 */
suspend fun WebSocketSession.close(reason: CloseReason) {
    send(Frame.Close(reason))
    try {
        flush()
    } catch (ignore: ClosedSendChannelException) {
    }
}

/**
 * Enqueues a text frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
suspend fun WebSocketSession.send(content: String) = send(Frame.Text(content))

/**
 * Enqueues a final binary frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
suspend fun WebSocketSession.send(content: ByteArray) = send(Frame.Binary(true, ByteBuffer.wrap(content)))
