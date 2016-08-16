package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost>() {
    override fun createServer(block: Routing.() -> Unit): TomcatApplicationHost {
        return embeddedTomcatServer(port, application = block)
    }
}
