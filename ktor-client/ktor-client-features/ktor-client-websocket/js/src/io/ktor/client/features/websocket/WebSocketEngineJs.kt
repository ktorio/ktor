package io.ktor.client.features.websocket

import io.ktor.client.request.*
import io.ktor.util.date.*
import kotlinx.coroutines.*
import org.w3c.dom.*
import kotlin.coroutines.*

internal actual fun findWebSocketEngine(): WebSocketEngine = DefaultJsWebSocketEngine()

internal class DefaultJsWebSocketEngine() : WebSocketEngine {
    override val coroutineContext: CoroutineContext = Dispatchers.Default

    override suspend fun execute(request: HttpRequest): WebSocketResponse {
        val requestTime = GMTDate()
        val callContext = CompletableDeferred<Unit>() + coroutineContext

        val socket = WebSocket(request.url.toString()).apply { await() }
        val session = JsWebSocketSession(callContext, socket)

        return WebSocketResponse(callContext, requestTime, session)
    }

    private suspend fun WebSocket.await(): Unit = suspendCancellableCoroutine { continuation ->
        onopen = {
            onopen = undefined
            onerror = undefined
            continuation.resume(Unit)
        }
        onerror = {
            continuation.resumeWithException(WebSocketException("$it"))
        }
    }
}
