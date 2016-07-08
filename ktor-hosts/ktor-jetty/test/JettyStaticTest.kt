package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*

class JettyStaticTest : HostTestSuite() {

    override fun createServer(block: Routing.() -> Unit): ApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment {}

        return embeddedJettyServer(config, env, application = block)
    }
}