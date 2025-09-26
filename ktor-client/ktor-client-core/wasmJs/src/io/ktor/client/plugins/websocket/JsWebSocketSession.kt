/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.websocket

import io.ktor.client.utils.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.khronos.webgl.*
import org.w3c.dom.*
import kotlin.coroutines.*

private fun tryGetEventDataAsString(data: JsAny): String? =
    js("typeof(data) === 'string' ? data : null")

private fun tryGetEventDataAsArrayBuffer(data: JsAny): ArrayBuffer? =
    js("data instanceof ArrayBuffer ? data : null")

internal class JsWebSocketSession(
    override val coroutineContext: CoroutineContext,
    private val websocket: WebSocket
) : DefaultWebSocketSession {
    private val _closeReason: CompletableDeferred<CloseReason> = CompletableDeferred()
    private val _incoming: Channel<Frame> = Channel(Channel.UNLIMITED)
    private val _outgoing: Channel<Frame> = Channel(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = _incoming
    override val outgoing: SendChannel<Frame> = _outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    override val closeReason: Deferred<CloseReason?> = _closeReason

    override var pingIntervalMillis: Long
        get() = throw WebSocketException("Websocket ping-pong is not supported in JS engine.")
        set(_) = throw WebSocketException("Websocket ping-pong is not supported in JS engine.")

    override var timeoutMillis: Long
        get() = throw WebSocketException("Websocket timeout is not supported in JS engine.")
        set(_) = throw WebSocketException("Websocket timeout is not supported in JS engine.")

    override var masking: Boolean
        get() = true
        set(_) = throw WebSocketException("Masking switch is not supported in JS engine.")

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) = throw WebSocketException("Max frame size switch is not supported in Js engine.")

    init {
        websocket.binaryType = BinaryType.ARRAYBUFFER

        websocket.addEventListener(
            "message"
        ) { it: JsAny ->
            val event = it.unsafeCast<MessageEvent>()

            val data = event.data
            if (data == null) {
                val error = IllegalStateException("Empty message - no data for: ${event.type}")
                _closeReason.completeExceptionally(error)
                throw error
            }

            val dataAsString = tryGetEventDataAsString(data)
            val frame: Frame = if (dataAsString != null) {
                Frame.Text(dataAsString)
            } else {
                val dataAsBuffer = tryGetEventDataAsArrayBuffer(data)
                if (dataAsBuffer != null) {
                    Frame.Binary(true, Uint8Array(dataAsBuffer).asByteArray())
                } else {
                    val error = IllegalStateException("Unknown frame type: ${event.type}")
                    _closeReason.completeExceptionally(error)
                    throw error
                }
            }

            _incoming.trySend(frame)
        }

        websocket.addEventListener(
            "error"
        ) { it: JsAny ->
            val cause = WebSocketException("$it")
            _closeReason.completeExceptionally(cause)
            _incoming.close(cause)
            _outgoing.cancel()
        }

        websocket.addEventListener(
            "close"
        ) { it: JsAny ->
            val closeEvent = it.unsafeCast<CloseEvent>()
            val reason = CloseReason(closeEvent.code, closeEvent.reason)
            _closeReason.complete(reason)
            _incoming.trySend(Frame.Close(reason))
            _incoming.close()
            _outgoing.cancel()
        }

        launch {
            _outgoing.consumeEach {
                when (it.frameType) {
                    FrameType.TEXT -> {
                        val text = it.data

                        websocket.send(text.decodeToString(0, 0 + text.size))
                    }
                    FrameType.BINARY -> {
                        val source = it.data.asJsArray()
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
                // We cannot use INTERNAL_ERROR similarly to other WebSocketSession implementations here
                // as sending it is not supported by browsers.
                websocket.close(CloseReason.Codes.NORMAL.code, "Client failed")
            }
        }
    }

    @OptIn(InternalAPI::class)
    override fun start(negotiatedExtensions: List<WebSocketExtension<*>>) {
        require(negotiatedExtensions.isEmpty()) { "Extensions are not supported." }
    }

    override suspend fun flush() {
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        level = DeprecationLevel.ERROR
    )
    override fun terminate() {
        _incoming.cancel()
        _outgoing.cancel()
        _closeReason.cancel("WebSocket terminated")
        websocket.close()
    }

    @OptIn(InternalAPI::class)
    private fun Short.isReservedStatusCode(): Boolean {
        return CloseReason.Codes.byCode(this).let { resolved ->

            resolved == null || resolved == CloseReason.Codes.CLOSED_ABNORMALLY
        }
    }
}
