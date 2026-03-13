/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.future.await
import java.net.http.*
import java.nio.ByteBuffer
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletionStage
import kotlin.coroutines.CoroutineContext
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
    @OptIn(InternalAPI::class)
    val wsConfig = requestData.attributes[WEBSOCKETS_KEY]
    val webSocket = JavaHttpWebSocket(
        callContext = coroutineContext,
        httpClient = this,
        requestData = requestData,
        channelsConfig = wsConfig.channelsConfig
    )
    try {
        return webSocket.getResponse()
    } catch (cause: HttpConnectTimeoutException) {
        throw ConnectTimeoutException(requestData, cause)
    } catch (cause: HttpTimeoutException) {
        throw SocketTimeoutException(requestData, cause)
    }
}

@OptIn(DelicateCoroutinesApi::class, InternalAPI::class)
internal class JavaHttpWebSocket(
    private val callContext: CoroutineContext,
    private val httpClient: HttpClient,
    private val requestData: HttpRequestData,
    private val requestTime: GMTDate = GMTDate(),
    channelsConfig: WebSocketChannelsConfig
) : WebSocket.Listener, WebSocketSession {

    private var _webSocket: WebSocket? = null
    private val webSocket: WebSocket
        get() = checkNotNull(_webSocket) { "Web socket is not connected yet." }

    private val socketJob = Job(callContext[Job])
    private val _incoming = Channel.from<Frame>(channelsConfig.incoming)
    private val _outgoing = Channel.from<Frame>(channelsConfig.outgoing)

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

    override val extensions: List<WebSocketExtension<*>>
        get() = emptyList()

    init {
        launch(CoroutineName("java-ws-outgoing")) {
            _outgoing.consumeEach { frame ->
                webSocket.sendFrame(frame)
                if (frame.frameType == FrameType.CLOSE) {
                    socketJob.complete()
                    return@launch
                }
            }
        }

        GlobalScope.launch(callContext + CoroutineName("java-ws-closer"), start = CoroutineStart.ATOMIC) {
            try {
                socketJob[Job]!!.join()
            } catch (_: Throwable) {
                val code = CloseReason.Codes.INTERNAL_ERROR.code.toInt()
                _webSocket?.sendClose(code, "Client failed")
            } finally {
                _incoming.close()
                _outgoing.cancel()
            }
        }
    }

    private suspend fun WebSocket.sendFrame(frame: Frame) {
        when (frame.frameType) {
            FrameType.TEXT -> sendText(String(frame.data), frame.fin).await()
            FrameType.BINARY -> sendBinary(frame.buffer, frame.fin).await()
            FrameType.PING -> sendPing(frame.buffer).await()
            FrameType.PONG -> sendPong(frame.buffer).await()

            FrameType.CLOSE -> {
                val data = buildPacket { writeFully(frame.data) }
                val code = data.readShort().toInt()
                val reason = data.readText()
                sendClose(code, reason).await()
            }
        }
    }

    @OptIn(InternalAPI::class)
    suspend fun getResponse(): HttpResponseData {
        val builder = httpClient.newWebSocketBuilder()

        with(builder) {
            requestData.getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttributes ->
                timeoutAttributes.connectTimeoutMillis?.let {
                    connectTimeout(Duration.ofMillis(it))
                }
            }

            mergeHeaders(requestData.headers, requestData.body) { key, value ->
                if (!ILLEGAL_HEADERS.contains(key) && !DISALLOWED_HEADERS.contains(key)) {
                    header(key, value)
                }
            }

            requestData.headers.getAll(HttpHeaders.SecWebSocketProtocol)?.toTypedArray()?.let {
                if (it.isNotEmpty()) {
                    val mostPreferred = it.first()
                    val leastPreferred = it.sliceArray(1..<it.size)
                    subprotocols(mostPreferred, *leastPreferred)
                }
            }
        }

        var status = HttpStatusCode.SwitchingProtocols
        var headers: Headers
        try {
            _webSocket = builder.buildAsync(requestData.url.toURI(), this).await()
            val protocol = webSocket.subprotocol?.takeIf { it.isNotEmpty() }
            headers = if (protocol != null) headersOf(HttpHeaders.SecWebSocketProtocol, protocol) else Headers.Empty
        } catch (cause: WebSocketHandshakeException) {
            if (cause.response.statusCode() == HttpStatusCode.Unauthorized.value) {
                status = HttpStatusCode.Unauthorized
                headers = headersOf(cause.response.headers().map())
            } else {
                throw cause
            }
        }

        return HttpResponseData(
            status,
            requestTime,
            headers,
            HttpProtocolVersion.HTTP_1_1,
            this,
            callContext
        )
    }

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
    }

    private inline fun receiveFrame(frame: Frame, crossinline onSent: () -> Unit): CompletionStage<*>? {
        val result = _incoming.trySend(frame)
        return when {
            result.isSuccess -> {
                onSent()
                null
            }

            result.isClosed -> result.exceptionOrNull()?.let { throw it }
            else -> async(start = CoroutineStart.UNDISPATCHED) {
                _incoming.send(frame)
                onSent()
            }.asCompletableFuture()
        }
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        val frame = Frame.Text(fin = last, data.toString().toByteArray())
        return receiveFrame(frame) { webSocket.request(1) }
    }

    override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
        val frame = Frame.Binary(fin = last, buffer = data)
        return receiveFrame(frame) { webSocket.request(1) }
    }

    override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
        val frame = Frame.Pong(buffer = message)
        return receiveFrame(frame) { webSocket.request(1) }
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        val cause = WebSocketException(error.message ?: "web socket failed", error)
        _incoming.close(cause)
        _outgoing.cancel()
        socketJob.complete()
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
        val closeReason = CloseReason(code = statusCode.toShort(), message = reason)
        return receiveFrame(Frame.Close(closeReason)) { socketJob.complete() }
    }

    override suspend fun flush() {
    }

    @Deprecated(
        "Use cancel() instead.",
        ReplaceWith("cancel()", "kotlinx.coroutines.cancel"),
        DeprecationLevel.ERROR
    )
    override fun terminate() {
        socketJob.cancel()
    }
}

private fun headersOf(map: Map<String, List<String>>): Headers = object : Headers {
    override val caseInsensitiveName: Boolean = true
    override fun getAll(name: String): List<String>? = map[name]
    override fun names(): Set<String> = map.keys
    override fun entries(): Set<Map.Entry<String, List<String>>> = map.entries
    override fun isEmpty(): Boolean = map.isEmpty()
}
