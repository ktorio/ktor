package org.jetbrains.ktor.netty.tests

import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.tests.application.*

class NettyStaticTest : HostTestSuite() {
    override fun createServer(port: Int, block: Routing.() -> Unit): ApplicationHost {
        return embeddedNettyServer(port, application = block)
    }
}