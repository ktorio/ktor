package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*

class NettyWebSocketTest : WebSocketHostSuite() {
    override fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost {
        return embeddedNettyServer(port, application = block)
    }
}