/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.engine.curl.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.atomicfu.atomic
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import libcurl.*
import kotlin.coroutines.CoroutineContext

@OptIn(InternalAPI::class, ExperimentalForeignApi::class)
internal class CurlWebSocketSession(
    private val websocket: CurlWebSocketResponseBody,
    callContext: CoroutineContext,
    outgoingFramesConfig: ChannelConfig,
    private val curlProcessor: CurlProcessor,
) : WebSocketSession, Closeable {

    private val closed = atomic(false)
    private val socketJob = Job(callContext[Job])
    private val _outgoing = Channel.from<Frame>(outgoingFramesConfig)

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

        launch(CoroutineName("curl-ws-outgoing")) {
            while (!closed.value) {
                val frame = _outgoing.receive()
                sendNextFrame(frame)
            }
        }
    }

    private suspend fun sendNextFrame(frame: Frame) {
        val flags = if (frame.fin) 0 else CURLWS_CONT
        when (frame.frameType) {
            FrameType.BINARY -> sendFrame(CURLWS_BINARY or flags, frame.data)
            FrameType.TEXT -> sendFrame(CURLWS_TEXT or flags, frame.data)
            FrameType.PING -> sendFrame(CURLWS_PING or flags, frame.data)
            FrameType.PONG -> sendFrame(CURLWS_PONG or flags, frame.data)

            FrameType.CLOSE -> {
                sendFrame(CURLWS_CLOSE or flags, frame.data)
                close(null)
                socketJob.complete()
            }
        }
    }

    private suspend fun sendFrame(flags: Int, data: ByteArray) {
        curlProcessor.sendWebSocketFrame(websocket, flags, data)
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

        websocket.close(cause)
        _outgoing.cancel()
    }
}
