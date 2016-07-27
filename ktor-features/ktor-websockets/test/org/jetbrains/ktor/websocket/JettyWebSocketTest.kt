package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*

class JettyWebSocketTest : WebSocketHostSuite() {
    override fun createServer(block: Routing.() -> Unit): ApplicationHost {
        return embeddedJettyServer(port, routing = block)
    }
}
