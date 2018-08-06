package io.ktor.http.websocket

import io.ktor.util.*


private const val WEBSOCKET_SERVER_ACCEPT_TAIL = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

fun websocketServerAccept(nonce: String): String =
    encodeBase64(sha1("${nonce.trim()}$WEBSOCKET_SERVER_ACCEPT_TAIL".toByteArray(Charsets.ISO_8859_1)))
