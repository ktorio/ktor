package io.ktor.websocket

import kotlinx.coroutines.experimental.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.util.*
import java.io.*
import java.security.*
import kotlin.coroutines.experimental.*

class WebSocketUpgrade(val call: ApplicationCall, val protocol: String? = null, val handle: suspend WebSocketSession.(Dispatchers) -> Unit) : FinalContent.ProtocolUpgrade() {
    private val key = call.request.header(HttpHeaders.SecWebSocketKey) ?: throw IllegalArgumentException("It should be ${HttpHeaders.SecWebSocketKey} header")

    override val status: HttpStatusCode?
        get() = HttpStatusCode.SwitchingProtocols

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

    override suspend fun upgrade(input: ReadChannel, output: WriteChannel, closeable: Closeable, hostContext: CoroutineContext, userAppContext: CoroutineContext): Closeable {
        val webSockets = call.application.feature(WebSockets)
        val webSocket = RawWebSocketImpl(call, input, output, closeable, NoPool, hostContext, userAppContext)

        webSocket.maxFrameSize = webSockets.maxFrameSize
        webSocket.masking = webSockets.masking

        webSocket.start(handle)

        return Closeable {
            runBlocking {
                webSocket.terminate()
            }
        }
    }

    class Dispatchers(val hostContext: CoroutineContext, val userAppContext: CoroutineContext)

    private fun sha1(s: String) = MessageDigest.getInstance("SHA1").digest(s.toByteArray(Charsets.ISO_8859_1))
}