package org.jetbrains.ktor.tests.tomcat

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tomcat.*
import org.junit.*
import java.util.logging.*

class TomcatHostTest : HostTestSuite<TomcatApplicationHost>() {
    // silence tomcat logger
    init {
        listOf("org.apache.coyote", "org.apache.tomcat", "org.apache.catalina").map {
            Logger.getLogger(it).apply { level = Level.WARNING }
        }
    }

    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, routing: Routing.() -> Unit): TomcatApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment(envInit)

        return embeddedTomcatServer(config, env, application = routing)
    }
}