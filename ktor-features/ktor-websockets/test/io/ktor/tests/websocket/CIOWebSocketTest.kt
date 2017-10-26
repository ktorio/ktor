package io.ktor.tests.websocket

import io.ktor.server.cio.*

class CIOWebSocketTest : WebSocketEngineSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}