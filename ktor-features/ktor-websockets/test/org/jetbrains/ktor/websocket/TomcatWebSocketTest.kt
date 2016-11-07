package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost>() {
    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, routing: Routing.() -> Unit): TomcatApplicationHost {
        val hostConfig = applicationHostConfig {
            connector {
                port = this@TomcatWebSocketTest.port
            }
        }
        val environmentConfig = applicationEnvironment(envInit)

        return embeddedTomcatServer(hostConfig, environmentConfig, application = routing)
    }
}
