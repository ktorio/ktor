package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.ktor.application.*

interface WebSocketSession {
    val call: ApplicationCall

    val application: Application

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

    // compatibility helpers, need to be member functions

    @Deprecated("Use incoming channel to handle frames", ReplaceWith("incoming.consumeEach(block)", "kotlinx.coroutines.experimental.channels.consumeEach"), level = DeprecationLevel.ERROR)
    fun handle(block: suspend WebSocketSession.(Frame) -> Unit): Unit = TODO()

    @Deprecated("Wrap your handler with try/finally and handle close accordingly", level = DeprecationLevel.ERROR)
    fun close(block: suspend WebSocketSession.(CloseReason?) -> Unit): Unit = TODO()
}

@Deprecated("Use WebSocketSession instead", ReplaceWith("WebSocketSession"))
typealias WebSocket = WebSocketSession

@Deprecated("Use send instead", ReplaceWith("send(frame)"))
fun WebSocketSession.enqueue(frame: Frame) {
    if (frame.frameType.controlFrame) {
        throw IllegalArgumentException("You should never enqueue control frames as they are delivery-time sensitive, use send() instead")
    }

    runBlocking {
        send(frame)
    }
}

suspend fun WebSocketSession.close(reason: CloseReason) {
    send(Frame.Close(reason))
    flush()
}