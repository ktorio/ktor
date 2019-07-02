/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.websocket

import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.core.*
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

    override val closeReason: Deferred<CloseReason?> = _closeReason

    init {
        websocket.binaryType = BinaryType.ARRAYBUFFER

        websocket.addEventListener("message", callback = {
            val event = it.unsafeCast<MessageEvent>()

            launch {
                val data = event.data

                val frame: Frame = when (data) {
                    is ArrayBuffer -> Frame.Binary(false, Int8Array(data).unsafeCast<ByteArray>())
                    is String -> Frame.Text(data)
                    else -> error("Unknown frame type: ${event.type}")
                }

                _incoming.offer(frame)
            }
        })

        websocket.addEventListener("error", callback = {
            _incoming.close(WebSocketException("$it"))
            _outgoing.cancel()
        })

        websocket.addEventListener("close", callback = { event: dynamic ->
            launch {
                _incoming.send(Frame.Close(CloseReason(event.code as Short, event.reason as String)))
                _incoming.close()

                _outgoing.cancel()
            }
        })

        launch {
            _outgoing.consumeEach {
                when (it.frameType) {
                    FrameType.TEXT -> {
                        val text = it.data
                        websocket.send(String(text))
                    }
                    FrameType.BINARY -> {
                        val source = it.data as Int8Array
                        val frameData = source.buffer.slice(
                            source.byteOffset, source.byteOffset + source.byteLength
                        )

                        websocket.send(frameData)
                    }
                    FrameType.CLOSE -> {
                        val data = buildPacket { writeFully(it.data) }
                        websocket.close(data.readShort(), data.readText())
                    }
                }
            }
        }

        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause == null) {
                websocket.close()
            } else {
                websocket.close(CloseReason.Codes.UNEXPECTED_CONDITION.code, "Client failed")
            }
        }
    }

    override suspend fun flush() {
    }

    override fun terminate() {
        _incoming.cancel()
        _outgoing.cancel()
        websocket.close()
    }

    @KtorExperimentalAPI
    override suspend fun close(cause: Throwable?) {
        val reason = cause?.let {
            CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, cause.message ?: "")
        } ?: CloseReason(CloseReason.Codes.NORMAL, "OK")

        websocket.close(reason.code, reason.message)
        _outgoing.close(cause)
        _incoming.close(cause)
    }
}
