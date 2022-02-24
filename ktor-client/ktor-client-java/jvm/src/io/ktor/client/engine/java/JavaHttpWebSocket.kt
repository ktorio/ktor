/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.future.*
import java.net.http.*
import java.nio.*
import java.time.*
import java.util.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.text.String
import kotlin.text.toByteArray

private val ILLEGAL_HEADERS = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
    addAll(
        setOf(
            HttpHeaders.SecWebSocketAccept,
            HttpHeaders.SecWebSocketExtensions,
            HttpHeaders.SecWebSocketKey,
            HttpHeaders.SecWebSocketProtocol,
            HttpHeaders.SecWebSocketVersion
        )
    )
}

internal suspend fun HttpClient.executeWebSocketRequest(
    coroutineContext: CoroutineContext,
    requestData: HttpRequestData
): HttpResponseData {
    val webSocket = JavaHttpWebSocket(coroutineContext, this, requestData)
    try {
        return webSocket.getResponse()
    } catch (cause: HttpConnectTimeoutException) {
        throw ConnectTimeoutException(requestData, cause)
    } catch (cause: HttpTimeoutException) {
        throw SocketTimeoutException(requestData, cause)
    }
}

internal class JavaHttpWebSocket(
    private val callContext: CoroutineContext,
    private val httpClient: HttpClient,
    private val requestData: HttpRequestData,
    private val requestTime: GMTDate = GMTDate()
) : WebSocket.Listener, WebSocketSession {

    private lateinit var webSocket: WebSocket
    private val socketJob = Job(callContext[Job])
    private val _incoming = Channel<Frame>(Channel.UNLIMITED)
    private val _outgoing = Channel<Frame>(Channel.UNLIMITED)

    override val coroutineContext: CoroutineContext
        get() = callContext + socketJob + CoroutineName("java-ws")

    override var masking: Boolean
        get() = true
        set(_) {}

    override var maxFrameSize: Long
        get() = Long.MAX_VALUE
        set(_) {}

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val outgoing: SendChannel<Frame>
        get() = _outgoing

    @ExperimentalWebSocketExtensionApi
    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    init {
        launch {
            @OptIn(ExperimentalCoroutinesApi::class)
            _outgoing.consumeEach { frame ->
                when (frame.frameType) {
                    FrameType.TEXT -> {
                        webSocket.sendText(String(frame.data), frame.fin).await()
                    }
                    FrameType.BINARY -> {
                        webSocket.sendBinary(frame.buffer, frame.fin).await()
                    }
                    FrameType.CLOSE -> {
                        val data = buildPacket { writeFully(frame.data) }
                        val code = data.readShort().toInt()
                        val reason = data.readText()
                        webSocket.sendClose(code, reason).await()
                        socketJob.complete()
                        return@launch
                    }
                    FrameType.PING -> {
                        webSocket.sendPing(frame.buffer).await()
                    }
                    FrameType.PONG -> {
                        webSocket.sendPong(frame.buffer).await()
                    }
                }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        GlobalScope.launch(callContext, start = CoroutineStart.ATOMIC) {
            try {
                socketJob[Job]!!.join()
            } catch (cause: Exception) {
                val code = CloseReason.Codes.INTERNAL_ERROR.code.toInt()
                webSocket.sendClose(code, "Client failed")
            } finally {
                _incoming.close()
                _outgoing.cancel()
            }
        }
    }

    suspend fun getResponse(): HttpResponseData {
        val builder = httpClient.newWebSocketBuilder()

        with(builder) {
            requestData.getCapabilityOrNull(HttpTimeout)?.let { timeoutAttributes ->
                timeoutAttributes.connectTimeoutMillis?.let {
                    connectTimeout(Duration.ofMillis(it))
                }
            }

            mergeHeaders(requestData.headers, requestData.body) { key, value ->
                if (!ILLEGAL_HEADERS.contains(key) && !DISALLOWED_HEADERS.contains(key)) {
                    header(key, value)
                }
            }
        }

        webSocket = builder.buildAsync(requestData.url.toURI(), this).await()

        return HttpResponseData(
            HttpStatusCode.OK,
            requestTime,
            Headers.Empty,
            HttpProtocolVersion.HTTP_1_1,
            this,
            callContext
        )
    }

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*> = async {
        _incoming.offer(Frame.Text(last, data.toString().toByteArray()))
        webSocket.request(1)
    }.asCompletableFuture()

    override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*> = async {
        _incoming.offer(Frame.Binary(last, data))
        webSocket.request(1)
    }.asCompletableFuture()

    override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> = async {
        _incoming.offer(Frame.Ping(message))
        webSocket.request(1)
    }.asCompletableFuture()

    override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*> = async {
        _incoming.offer(Frame.Pong(message))
        webSocket.request(1)
    }.asCompletableFuture()

    override fun onError(webSocket: WebSocket, error: Throwable) {
        val cause = WebSocketException("${error.message}")
        _incoming.close(cause)
        _outgoing.cancel()
        socketJob.complete()
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*> = async {
        val closeReason = CloseReason(statusCode.toShort(), reason)
        _incoming.send(Frame.Close(closeReason))
        socketJob.complete()
    }.asCompletableFuture()

    override suspend fun flush() {
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel")
    )
    override fun terminate() {
        socketJob.cancel()
    }
}
