/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.atomicfu.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import libcurl.*
import kotlin.coroutines.*

@OptIn(ExperimentalForeignApi::class)
internal class CurlWebSocketSession(
    private val websocket: CurlWebSocketResponseBody,
    callContext: CoroutineContext
) : WebSocketSession, Closeable {

    private val closed = atomic(false)
    private val socketJob = Job(callContext[Job])
    private val _outgoing = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext = callContext + socketJob + CoroutineName("curl-ws")
    override var masking: Boolean
        get() = true
        set(_) {}

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) {}

    override val incoming: ReceiveChannel<Frame>
        get() = websocket.incoming

    override val outgoing: SendChannel<Frame>
        get() = _outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    init {
        socketJob.invokeOnCompletion {
            close(it)
        }

        launch {
            while (!closed.value) {
                val frame = _outgoing.receive()
                sendNextFrame(frame)
            }
        }
    }

    private fun sendNextFrame(frame: Frame) {
        val flags = if (frame.fin) 0 else CURLWS_CONT
        when (frame.frameType) {
            FrameType.BINARY -> {
                websocket.sendFrame(CURLWS_BINARY or flags, frame.data)
            }

            FrameType.TEXT -> {
                websocket.sendFrame(CURLWS_TEXT or flags, frame.data)
            }

            FrameType.CLOSE -> {
                websocket.sendFrame(CURLWS_CLOSE or flags, frame.data)
                close(null)
                socketJob.complete()
            }

            FrameType.PING -> {
                websocket.sendFrame(CURLWS_PING or flags, frame.data)
            }

            FrameType.PONG -> {
                websocket.sendFrame(CURLWS_PONG or flags, frame.data)
            }

            else -> {
                throw IllegalArgumentException("Unknown frame type: $frame")
            }
        }
    }

    override suspend fun flush() = Unit

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        socketJob.cancel()
    }

    override fun close() {
        socketJob.complete()
    }

    private fun close(cause: Throwable?) {
        if (!closed.compareAndSet(expect = false, update = true)) return

        websocket.close()
        _outgoing.cancel()
    }
}
