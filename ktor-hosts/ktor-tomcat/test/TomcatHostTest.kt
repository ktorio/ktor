package org.jetbrains.ktor.tests.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*
import org.jetbrains.ktor.tomcat.*

class TomcatHostTest : HostTestSuite<TomcatApplicationHost>() {

    override fun createServer(block: Routing.() -> Unit): TomcatApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment {}

        return embeddedTomcatServer(config, env, application = block)
    }
}