package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.*

class NettyEngineTest : EngineTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}
