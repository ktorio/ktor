/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.websocket

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * Represents a web socket session between two peers
 */
public expect interface WebSocketSession : CoroutineScope {
    /**
     * Specifies frame size limit. Connection will be closed if violated
     */
    public var maxFrameSize: Long

    /**
     * Incoming frames channel
     */
    public val incoming: ReceiveChannel<Frame>

    /**
     * Outgoing frames channel. It could have limited capacity so sending too much frames may lead to suspension at
     * corresponding send invocations. It also may suspend if a peer doesn't read frames for some reason.
     */
    public val outgoing: SendChannel<Frame>

    /**
     * Negotiated WebSocket extensions.
     */
    @ExperimentalWebSocketExtensionApi
    public val extensions: List<WebSocketExtension<*>>

    /**
     * Enqueue frame, may suspend if outgoing queue is full. May throw an exception if outgoing channel is already
     * closed so it is impossible to transfer any message. Frames that were sent after close frame could be silently
     * ignored. Please note that close frame could be sent automatically in reply to a peer close frame unless it is
     * raw websocket session.
     */
    public suspend fun send(frame: Frame)

    /**
     * Flush all outstanding messages and suspend until all earlier sent messages will be written. Could be called
     * at any time even after close. May return immediately if the connection is already terminated.
     * However it may also fail with an exception (or cancellation) at any point due to session failure.
     * Please note that [flush] doesn't guarantee that frames were actually delivered.
     */
    public suspend fun flush()

    /**
     * Initiate connection termination immediately. Termination may complete asynchronously.
     */
    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel")
    )
    public fun terminate()
}

/**
 * Find the extensions using [WebSocketExtensionFactory].
 *
 * @return extension instance.
 * @throws [IllegalStateException] if the extension is not found.
 */
@ExperimentalWebSocketExtensionApi
public fun <T : WebSocketExtension<*>> WebSocketSession.extension(extension: WebSocketExtensionFactory<*, T>): T =
    extensionOrNull(extension) ?: error("Extension $extension not found.")

/**
 * Search the extensions using [WebSocketExtensionFactory].
 *
 * @return extension instance or `null` if the extension is not installed.
 */
@Suppress("UNCHECKED_CAST")
@ExperimentalWebSocketExtensionApi
public fun <T : WebSocketExtension<*>> WebSocketSession.extensionOrNull(
    extension: WebSocketExtensionFactory<*, T>
): T? = extensions.firstOrNull { it.factory.key === extension.key } as? T?

/**
 * Enqueues a text frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
public suspend fun WebSocketSession.send(content: String): Unit = send(Frame.Text(content))

/**
 * Enqueues a final binary frame for sending with the specified [content].
 *
 * May suspend if the outgoing queue is full, and throw an exception if the channel is already closed.
 */
public suspend fun WebSocketSession.send(content: ByteArray): Unit = send(Frame.Binary(true, content))

/**
 * Send a close frame with the specified [reason]. May suspend if outgoing channel is full.
 * The specified [reason] could be ignored if there was already
 * close frame sent (for example in reply to a peer close frame). It also may do nothing when a session or an outgoing
 * channel is already closed due to any reason.
 */
public suspend fun WebSocketSession.close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
    try {
        send(Frame.Close(reason))
        flush()
    } catch (_: Throwable) {
    }
}

/**
 * Closes with reason depending on [cause] or normally if [cause] is `null`.
 * This is going to be removed. Close with a particular reason or terminate instead.
 */
@Deprecated("Close with reason or terminate instead.")
public suspend fun WebSocketSession.close(cause: Throwable?) {
    if (cause == null) {
        close()
    } else {
        closeExceptionally(cause)
    }
}

/**
 * Closes session with normal or error close reason, depending on whether [cause] is cancellation or not.
 */
@InternalAPI
public suspend fun WebSocketSession.closeExceptionally(cause: Throwable) {
    val reason = when (cause) {
        is CancellationException -> CloseReason(CloseReason.Codes.NORMAL, "")
        else -> CloseReason(CloseReason.Codes.INTERNAL_ERROR, cause.toString())
    }

    close(reason)
}
