package io.ktor.websocket

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.*
import java.io.*
import java.security.*
import kotlin.coroutines.experimental.*

/**
 * An [OutgoingContent] response object that could be used to `respond()`: it will cause application engine to
 * perform HTTP upgrade and start websocket RAW session
 *
 * Please note that you generally shouldn't use this object directly but use [WebSockets] feature with routing builders
 * [webSocket] instead
 */
class WebSocketUpgrade(val call: ApplicationCall, val protocol: String? = null, val handle: suspend WebSocketSession.(Dispatchers) -> Unit) : OutgoingContent.ProtocolUpgrade() {
    private val key = call.request.header(HttpHeaders.SecWebSocketKey) ?: throw IllegalArgumentException("It should be ${HttpHeaders.SecWebSocketKey} header")

    override val headers: ValuesMap
        get() = ValuesMap.build(true) {
            append(HttpHeaders.Upgrade, "websocket")
            append(HttpHeaders.Connection, "Upgrade")
            append(HttpHeaders.SecWebSocketAccept, encodeBase64(sha1("${key.trim()}258EAFA5-E914-47DA-95CA-C5AB0DC85B11")))
            if (protocol != null) {
                append(HttpHeaders.SecWebSocketProtocol, protocol)
            }
            // TODO extensions
        }

    override suspend fun upgrade(input: ByteReadChannel, output: ByteWriteChannel, engineContext: CoroutineContext, userContext: CoroutineContext): Job {
        val webSockets = call.application.feature(WebSockets)
        val webSocket = RawWebSocketImpl(call, input, output, NoPool, engineContext, userContext)

        webSocket.maxFrameSize = webSockets.maxFrameSize
        webSocket.masking = webSockets.masking

        return webSocket.start(handle)
    }

    class Dispatchers(val engineContext: CoroutineContext, val userContext: CoroutineContext)

    private fun sha1(s: String) = MessageDigest.getInstance("SHA1").digest(s.toByteArray(Charsets.ISO_8859_1))
}