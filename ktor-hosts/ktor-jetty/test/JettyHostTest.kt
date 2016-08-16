package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*

class JettyHostTest : HostTestSuite<JettyApplicationHost>() {

    override fun createServer(block: Routing.() -> Unit): JettyApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment {}

        return embeddedJettyServer(config, env, routing = block)
    }
}