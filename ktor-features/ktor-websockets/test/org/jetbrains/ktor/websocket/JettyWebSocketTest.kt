package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost>() {
    override fun createServer(block: Routing.() -> Unit): JettyApplicationHost {
        return embeddedJettyServer(port, routing = block)
    }
}
