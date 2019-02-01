package io.ktor.http.websocket

import io.ktor.util.*
import kotlinx.io.charsets.*
import kotlinx.io.core.*


private const val WEBSOCKET_SERVER_ACCEPT_TAIL = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

/**
 * Calculates `Sec-WebSocket-Accept` header value
 */
@KtorExperimentalAPI
fun websocketServerAccept(nonce: String): String =
    sha1("${nonce.trim()}$WEBSOCKET_SERVER_ACCEPT_TAIL".toByteArray(Charset.forName("ISO_8859_1"))).encodeBase64()
