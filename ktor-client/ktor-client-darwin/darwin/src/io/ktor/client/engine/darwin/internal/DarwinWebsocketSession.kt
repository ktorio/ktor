/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.readByteArray
import platform.Foundation.*
import platform.darwin.NSInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
internal class DarwinWebsocketSession(
    callContext: CoroutineContext,
    private val task: NSURLSessionWebSocketTask,
) : WebSocketSession {

    private val requestTime: GMTDate = GMTDate()
    val response = CompletableDeferred<HttpResponseData>()

    private val _incoming = Channel<Frame>(Channel.UNLIMITED)
    private val _outgoing = Channel<Frame>(Channel.UNLIMITED)
    private val socketJob = Job(callContext[Job])
    override val coroutineContext: CoroutineContext = callContext + socketJob

    override var masking: Boolean
        get() = true
        set(_) {}

    @OptIn(ExperimentalForeignApi::class)
    override var maxFrameSize: Long
        get() = task.maximumMessageSize.convert()
        set(value) {
            task.setMaximumMessageSize(value.convert())
        }

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val outgoing: SendChannel<Frame>
        get() = _outgoing

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    init {
        launch {
            receiveMessages()
        }
        launch {
            sendMessages()
        }
        coroutineContext[Job]!!.invokeOnCompletion { cause ->
            if (cause != null) {
                val code = CloseReason.Codes.INTERNAL_ERROR.code.convert<NSInteger>()
                task.cancelWithCloseCode(code, "Client failed".toByteArray().toNSData())
            }
            _incoming.close()
            _outgoing.cancel()
        }
    }

    private suspend fun receiveMessages() {
        while (true) {
            val message = task.receiveMessage()
            val frame = when (message.type) {
                NSURLSessionWebSocketMessageTypeData ->
                    Frame.Binary(true, message.data()!!.toByteArray())

                NSURLSessionWebSocketMessageTypeString ->
                    Frame.Text(true, message.string()!!.toByteArray())

                else -> throw IllegalArgumentException("Unknown message $message")
            }
            _incoming.send(frame)
        }
    }

    private suspend fun sendMessages() {
        _outgoing.consumeEach { frame ->
            when (frame.frameType) {
                FrameType.TEXT -> {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        task.sendMessage(
                            NSURLSessionWebSocketMessage(
                                frame.data.decodeToString(
                                    0,
                                    0 + frame.data.size
                                )
                            )
                        ) { error ->
                            if (error == null) {
                                continuation.resume(Unit)
                            } else {
                                continuation.resumeWithException(DarwinHttpRequestException(error))
                            }
                        }
                    }
                }

                FrameType.BINARY -> {
                    suspendCancellableCoroutine { continuation ->
                        task.sendMessage(NSURLSessionWebSocketMessage(frame.data.toNSData())) { error ->
                            if (error == null) {
                                continuation.resume(Unit)
                            } else {
                                continuation.resumeWithException(DarwinHttpRequestException(error))
                            }
                        }
                    }
                }

                FrameType.CLOSE -> {
                    val data = buildPacket { writeFully(frame.data) }
                    val code = data.readShort().convert<NSInteger>()
                    val reason = data.readByteArray()
                    task.cancelWithCloseCode(code, reason.toNSData())
                    return@sendMessages
                }

                FrameType.PING -> {
                    val payload = frame.readBytes()
                    task.sendPingWithPongReceiveHandler { error ->
                        if (error != null) {
                            cancel("Error receiving pong", DarwinHttpRequestException(error))
                            return@sendPingWithPongReceiveHandler
                        }
                        _incoming.trySend(Frame.Pong(payload))
                    }
                }

                else -> {
                    throw IllegalArgumentException("Unknown frame type: $frame")
                }
            }
        }
    }

    override suspend fun flush() {}

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        DeprecationLevel.ERROR
    )
    override fun terminate() {
        task.cancelWithCloseCode(CloseReason.Codes.NORMAL.code.convert(), null)
        coroutineContext.cancel()
    }

    fun didOpen(protocol: String?) {
        val headers = if (protocol != null) headersOf(HttpHeaders.SecWebSocketProtocol, protocol) else Headers.Empty

        val response = HttpResponseData(
            task.getStatusCode()?.let { HttpStatusCode.fromValue(it) } ?: HttpStatusCode.SwitchingProtocols,
            requestTime,
            headers,
            HttpProtocolVersion.HTTP_1_1,
            this,
            coroutineContext
        )
        this.response.complete(response)
    }

    fun didComplete(error: NSError?) {
        if (error == null) {
            socketJob.cancel()
            return
        }

        // KTOR-7363 We want to proceed with the request if we get 401 Unauthorized status code
        if (task.getStatusCode() == HttpStatusCode.Unauthorized.value) {
            didOpen(protocol = null)
            socketJob.complete()
            return
        }

        val exception = DarwinHttpRequestException(error)
        response.completeExceptionally(exception)
        socketJob.completeExceptionally(exception)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun didClose(
        code: NSURLSessionWebSocketCloseCode,
        reason: NSData?,
        webSocketTask: NSURLSessionWebSocketTask
    ) {
        val closeReason =
            CloseReason(code.toShort(), reason?.toByteArray()?.let { it.decodeToString(0, 0 + it.size) } ?: "")
        if (!_incoming.isClosedForSend) {
            _incoming.trySend(Frame.Close(closeReason))
        }
        socketJob.cancel()
        webSocketTask.cancelWithCloseCode(code, reason)
    }
}

private suspend fun NSURLSessionWebSocketTask.receiveMessage(): NSURLSessionWebSocketMessage =
    suspendCancellableCoroutine {
        receiveMessageWithCompletionHandler { message, error ->
            if (error != null) {
                // KTOR-7363 We want to proceed with the request if we get 401 Unauthorized status code
                if (getStatusCode() == HttpStatusCode.Unauthorized.value) {
                    it.cancel()
                    return@receiveMessageWithCompletionHandler
                }

                it.resumeWithException(DarwinHttpRequestException(error))
                return@receiveMessageWithCompletionHandler
            }
            if (message == null) {
                it.resumeWithException(IllegalArgumentException("Received null message"))
                return@receiveMessageWithCompletionHandler
            }

            it.resume(message)
        }
    }

@OptIn(UnsafeNumber::class)
internal fun NSURLSessionTask.getStatusCode() = (response() as NSHTTPURLResponse?)?.statusCode?.toInt()
