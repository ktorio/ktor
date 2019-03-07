package io.ktor.http.cio.websocket

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * Represents a web socket session between two peers
 */
expect interface WebSocketSession : CoroutineScope {
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
     * Enqueue frame, may suspend if outgoing queue is full. May throw an exception if outgoing channel is already
     * closed so it is impossible to transfer any message. Frames that were sent after close frame could be silently
     * ignored. Please note that close frame could be sent automatically in reply to a peer close frame unless it is
     * raw websocket session.
     */
    suspend fun send(frame: Frame)

    /**
     * Flush all outstanding messages and suspend until all earlier sent messages will be written. Could be called
     * at any time even after close. May return immediately if the connection is already terminated.
     * However it may also fail with an exception (or cancellation) at any point due to session failure.
     * Please note that [flush] doesn't guarantee that frames were actually delivered.
     */
    suspend fun flush()

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
 * Enqueues a text frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
suspend fun WebSocketSession.send(content: String): Unit = send(Frame.Text(content))

/**
 * Enqueues a final binary frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
suspend fun WebSocketSession.send(content: ByteArray): Unit = send(Frame.Binary(true, content))

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
