package org.jetbrains.ktor.netty.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*

class NettyHostTest : HostTestSuite<NettyApplicationHost>() {
    override fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, block: Routing.() -> Unit): NettyApplicationHost {
        val config = hostConfig(port, sslPort)
        val env = applicationEnvironment(envInit)

        return embeddedNettyServer(config, env, routing = block)
    }
}