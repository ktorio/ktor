package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.*
import org.junit.*

class NettyHostTest : HostTestSuite<NettyApplicationHost, NettyApplicationHost.Configuration>(Netty) {

    @Test
    @Ignore // runs too long
    override fun testBlockingDeadlock() {
        super.testBlockingDeadlock()
    }
}