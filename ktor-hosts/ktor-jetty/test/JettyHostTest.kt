package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*

class JettyHostTest : HostTestSuite<JettyApplicationHost>() {

    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, routing: Routing.() -> Unit): JettyApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment(envInit)

        return embeddedJettyServer(config, env) {
            install(Routing, routing)
        }
    }
}