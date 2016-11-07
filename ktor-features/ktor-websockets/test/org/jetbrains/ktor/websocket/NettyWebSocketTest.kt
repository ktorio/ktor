package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost>() {
    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, routing: Routing.() -> Unit): NettyApplicationHost {
        val hostConfig = applicationHostConfig {
            connector {
                port = this@NettyWebSocketTest.port
            }
        }
        val environmentConfig = applicationEnvironment(envInit)

        return embeddedNettyServer(hostConfig, environmentConfig) {
            install(Routing, routing)
        }
    }
}