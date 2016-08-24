package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost>() {
    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, block: Routing.() -> Unit): JettyApplicationHost {
        val _port = this.port

        val hostConfig = applicationHostConfig {
            connector {
                port = _port
            }
        }
        val environmentConfig = applicationEnvironment(envInit)

        return embeddedJettyServer(hostConfig, routing = block, environment = environmentConfig)
    }
}
