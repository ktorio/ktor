package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.websocket.*
import io.ktor.request.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

/**
 * An [OutgoingContent] response object that could be used to `respond()`: it will cause application engine to
 * perform HTTP upgrade and start websocket RAW session
 *
 * Please note that you generally shouldn't use this object directly but use [WebSockets] feature with routing builders
 * [webSocket] instead
 */
class WebSocketUpgrade(
    val call: ApplicationCall,
    val protocol: String? = null,
    val handle: suspend WebSocketSession.() -> Unit
) : OutgoingContent.ProtocolUpgrade() {
    private val key = call.request.header(HttpHeaders.SecWebSocketKey)

    override val headers: Headers
        get() = Headers.build {
            append(HttpHeaders.Upgrade, "websocket")
            append(HttpHeaders.Connection, "Upgrade")
            if (key != null) {
                append(HttpHeaders.SecWebSocketAccept, websocketServerAccept(key))
            }
            if (protocol != null) {
                append(HttpHeaders.SecWebSocketProtocol, protocol)
            }
        }

    override suspend fun upgrade(
        input: ByteReadChannel,
        output: ByteWriteChannel,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ): Job {
        val feature = call.application.feature(WebSockets)
        val webSocket = RawWebSocket(
            input, output,
            feature.maxFrameSize, feature.masking,
            dispatcher = engineContext
        )

        return launch(userContext, parent = feature.context) {
            try {
                webSocket.start(handle)
            } catch (cause: Throwable) {
            }
        }
    }
}
