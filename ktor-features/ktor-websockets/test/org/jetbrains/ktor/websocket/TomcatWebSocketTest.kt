package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite() {
    override fun createServer(block: Routing.() -> Unit): ApplicationHost {
        return embeddedTomcatServer(port, application = block)
    }
}
