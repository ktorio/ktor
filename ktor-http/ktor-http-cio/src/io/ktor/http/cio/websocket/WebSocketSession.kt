package io.ktor.http.cio.websocket

import kotlinx.coroutines.experimental.channels.*
import kotlin.coroutines.experimental.*

interface WebSocketSession {
    /**
     * Incoming frames channel
     */
    val incoming: ReceiveChannel<Frame>

    /**
     * Outgoing frames channel
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
    val dispatcher: CoroutineContext

    /**
     * Flush all outstanding messages and suspend until all earlier sent messages will be written. Could be called
     * at any time even after close. May return immediately if connection is already terminated.
     */
    suspend fun flush()

    /**
     * Enqueue frame, may suspend if outgoing queue is full. May throw an exception if outgoing channel is already
     * closed so it is impossible to transfer any message.
     */
    suspend fun send(frame: Frame) {
        outgoing.send(frame)
    }

    /**
     * Initiate connection termination immediately. Termination may complete asynchronously.
     */
    fun terminate()

    suspend fun close(cause: Throwable? = null)
}

suspend fun WebSocketSession.close(reason: CloseReason) {
    send(Frame.Close(reason))
    flush()
}
