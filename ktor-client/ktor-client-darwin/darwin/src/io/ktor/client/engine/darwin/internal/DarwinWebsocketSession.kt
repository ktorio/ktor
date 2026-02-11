/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.darwin.internal

import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.io.readByteArray
import platform.Foundation.*
import platform.darwin.NSInteger
import platform.posix.EMSGSIZE
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(InternalAPI::class, UnsafeNumber::class, ExperimentalForeignApi::class)
internal class DarwinWebsocketSession(
    callContext: CoroutineContext,
    private val task: NSURLSessionWebSocketTask,
    channelsConfig: WebSocketChannelsConfig
) : WebSocketSession {

    private val requestTime: GMTDate = GMTDate()
    val response = CompletableDeferred<HttpResponseData>()

    private val _incoming = Channel.from<Frame>(channelsConfig.incoming)
    private val _outgoing = Channel.from<Frame>(channelsConfig.outgoing)
    private val socketJob = Job(callContext[Job])
    override val coroutineContext: CoroutineContext = callContext + socketJob

    override var masking: Boolean
        get() = true
        set(_) = throw WebSocketException("Masking switch is not supported in Darwin engine.")

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
            _incoming.close(cause)
            _outgoing.cancel(cause = CancellationException(cause))
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

    private fun receiveFrame(frame: Frame) {
        val result = _incoming.trySend(frame)
        when {
            result.isSuccess -> return
            result.isClosed -> result.exceptionOrNull()?.let { throw it }
            else -> launch(start = CoroutineStart.UNDISPATCHED) {
                _incoming.send(frame)
            }
        }
    }

    private suspend fun sendMessages() {
        _outgoing.consumeEach { frame ->
            when (frame.frameType) {
                FrameType.TEXT -> {
                    suspendCancellableCoroutine { continuation ->
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
                        receiveFrame(Frame.Pong(payload))
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

        val exception = convertWebsocketError(error)
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

@OptIn(UnsafeNumber::class)
private suspend fun NSURLSessionWebSocketTask.receiveMessage(): NSURLSessionWebSocketMessage =
    suspendCancellableCoroutine {
        receiveMessageWithCompletionHandler { message, error ->
            if (error != null) {
                // KTOR-7363 We want to proceed with the request if we get 401 Unauthorized status code
                // KTOR-6198 We want to set correct close code and reason on URLSession:webSocketTask:didCloseWithCode:reason:
                if ((getStatusCode() == HttpStatusCode.Unauthorized.value) ||
                    (this.closeCode != NSURLSessionWebSocketCloseCodeInvalid)
                ) {
                    it.cancel()
                    return@receiveMessageWithCompletionHandler
                }

                it.resumeWithException(convertWebsocketError(error))
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
@Suppress("REDUNDANT_CALL_OF_CONVERSION_METHOD")
internal fun NSURLSessionTask.getStatusCode() = (response() as NSHTTPURLResponse?)?.statusCode?.toInt()

@OptIn(UnsafeNumber::class, ExperimentalForeignApi::class)
private fun convertWebsocketError(error: NSError): Exception = when {
    error.domain == NSPOSIXErrorDomain && error.code.convert<Int>() == EMSGSIZE -> {
        FrameTooBigException(frameSize = -1L, DarwinHttpRequestException(error))
    }
    else -> DarwinHttpRequestException(error)
}
