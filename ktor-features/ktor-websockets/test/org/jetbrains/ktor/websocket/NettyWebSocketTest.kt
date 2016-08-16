package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost>() {
    override fun createServer(block: Routing.() -> Unit): NettyApplicationHost {
        return embeddedNettyServer(port, routing = block)
    }
}