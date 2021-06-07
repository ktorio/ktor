/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import org.w3c.dom.*
import kotlin.coroutines.*

internal class JsWebSocketSession(
    override val coroutineContext: CoroutineContext,
    private val websocket: WebSocket
) : DefaultWebSocketSession {
    private val _closeReason: CompletableDeferred<CloseReason> = CompletableDeferred()
    private val _incoming: Channel<Frame> = Channel(Channel.UNLIMITED)
    private val _outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = _incoming
    override val outgoing: SendChannel<Frame> = _outgoing

    @ExperimentalWebSocketExtensionApi
    override val extensions: List<WebSocketExtension<*>> get() = emptyList()

    override val closeReason: Deferred<CloseReason?> = _closeReason

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(value) {}

    init {
        websocket.binaryType = BinaryType.ARRAYBUFFER

        websocket.addEventListener(
            "message",
            callback = {
                val event = it.unsafeCast<MessageEvent>()

                launch {
                    val data = event.data

                    val frame: Frame = when (data) {
                        is ArrayBuffer -> Frame.Binary(false, Int8Array(data).unsafeCast<ByteArray>())
                        is String -> Frame.Text(data)
                        else -> {
                            val error = IllegalStateException("Unknown frame type: ${event.type}")
                            _closeReason.completeExceptionally(error)
                            throw error
                        }
                    }

                    _incoming.offer(frame)
                }
            }
        )

        websocket.addEventListener(
            "error",
            callback = {
                val cause = WebSocketException("$it")
                _closeReason.completeExceptionally(cause)
                _incoming.close(cause)
                _outgoing.cancel()
            }
        )

        websocket.addEventListener(
            "close",
            callback = { event: dynamic ->
                launch {
                    val reason = CloseReason(event.code as Short, event.reason as String)
                    _closeReason.complete(reason)
                    _incoming.send(Frame.Close(reason))
                    _incoming.close()

                    _outgoing.cancel()
                }
            }
        )

        launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _outgoing.consumeEach {
                when (it.frameType) {
                    FrameType.TEXT -> {
                        val text = it.data
                        websocket.send(String(text))
                    }
                    FrameType.BINARY -> {
                        val source = it.data as Int8Array
                        val frameData = source.buffer.slice(
                            source.byteOffset,
                            source.byteOffset + source.byteLength
                        )

                        websocket.send(frameData)
                    }
                    FrameType.CLOSE -> {
                        val data = buildPacket { writeFully(it.data) }
                        val code = data.readShort()
                        val reason = data.readText()
                        _closeReason.complete(CloseReason(code, reason))
                        if (code.isReservedStatusCode()) {
                            websocket.close()
                        } else {
                            websocket.close(code, reason)
                        }
                    }
                    FrameType.PING, FrameType.PONG -> {
                        // ignore
                    }
                }
            }
        }

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause == null) {
                websocket.close()
            } else {
                websocket.close(CloseReason.Codes.INTERNAL_ERROR.code, "Client failed")
            }
        }
    }

    @OptIn(ExperimentalWebSocketExtensionApi::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        require(negotiatedExtensions.isEmpty()) { "Extensions are not supported." }
    }

    override suspend fun flush() {
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel")
    )
    override fun terminate() {
        _incoming.cancel()
        _outgoing.cancel()
        _closeReason.cancel("WebSocket terminated")
        websocket.close()
    }

    private fun Short.isReservedStatusCode(): Boolean {
        return CloseReason.Codes.byCode(this).let { resolved ->
            @Suppress("DEPRECATION")
            resolved == null || resolved == CloseReason.Codes.CLOSED_ABNORMALLY
        }
    }
}
