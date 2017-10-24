package io.ktor.tests.websocket

import io.ktor.server.cio.*

class CIOWebSocketTest : WebSocketHostSuite<CIOApplicationHost, CIOApplicationHost.Configuration>(CIO) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}