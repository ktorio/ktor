package org.jetbrains.ktor.tests.jetty

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*

class JettyStaticTest : HostTestSuite() {

    override fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost {
        return embeddedJettyServer(port, application = block)
    }
}