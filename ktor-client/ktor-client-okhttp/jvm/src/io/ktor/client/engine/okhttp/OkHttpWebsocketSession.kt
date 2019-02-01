package io.ktor.client.engine.okhttp

import io.ktor.client.features.websocket.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import okhttp3.*
import okio.*
import kotlin.coroutines.*

internal class OkHttpWebsocketSession(
    private val engine: OkHttpClient,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext
) : DefaultWebSocketSession, WebSocketListener() {
    private val websocket: WebSocket = engine.newWebSocket(engineRequest, this)

    override var pingIntervalMillis: Long
        get() = engine.pingIntervalMillis().toLong()
        set(_) = throw WebSocketException("OkHttp doesn't support dynamic ping interval. You could switch it in the engine configuration.")

    override var timeoutMillis: Long
        get() = engine.readTimeoutMillis().toLong()
        set(_) = throw WebSocketException("Websocket timeout should be configured in OkHttpEngine.")

    override var masking: Boolean
        get() = true
        set(_) = throw WebSocketException("Masking switch is not supported in OkHttp engine.")

    override var maxFrameSize: Long
        get() = throw WebSocketException("OkHttp websocket doesn't support max frame size.")
        set(_) = throw WebSocketException("Websocket timeout should be configured in OkHttpEngine.")

    private val _incoming = Channel<Frame>()
    private val _closeReason = CompletableDeferred<CloseReason?>()

    override val incoming: ReceiveChannel<Frame>
        get() = _incoming

    override val closeReason: Deferred<CloseReason?>
        get() = _closeReason

    override val outgoing: SendChannel<Frame> = actor {
        try {
            for (frame in channel) {
                when (frame) {
                    is Frame.Binary -> websocket.send(ByteString.of(frame.data, 0, frame.data.size))
                    is Frame.Text -> websocket.send(String(frame.data))
                    is Frame.Close -> {
                        val reason = frame.readReason()!!
                        websocket.close(reason.code.toInt(), reason.message)
                        return@actor
                    }
                    else -> throw UnsupportedFrameTypeException(frame)
                }
            }
        } finally {
            websocket.close(CloseReason.Codes.UNEXPECTED_CONDITION.code.toInt(), "Client failure")
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        _incoming.sendBlocking(Frame.Binary(true, bytes.toByteArray()))
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        _incoming.sendBlocking(Frame.Text(true, text.toByteArray()))
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)

        _closeReason.complete(CloseReason(code.toShort(), reason))
        _incoming.close()
        outgoing.close()
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)

        _incoming.close(t)
        outgoing.close(t)
    }

    override suspend fun flush() {
    }

    override fun terminate() {
        coroutineContext.cancel()
    }

    @KtorExperimentalAPI
    override suspend fun close(cause: Throwable?) {
        outgoing.close(cause)
    }
}

@Suppress("KDocMissingDocumentation")
class UnsupportedFrameTypeException(frame: Frame) : IllegalArgumentException("Unsupported frame type: $frame")
