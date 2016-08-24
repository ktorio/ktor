package org.jetbrains.ktor.tests.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tomcat.*

class TomcatHostTest : org.jetbrains.ktor.testing.HostTestSuite<TomcatApplicationHost>() {

    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, block: Routing.() -> Unit): TomcatApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment(envInit)

        return embeddedTomcatServer(config, env, application = block)
    }
}