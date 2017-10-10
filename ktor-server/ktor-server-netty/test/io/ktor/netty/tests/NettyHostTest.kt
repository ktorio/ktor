package io.ktor.netty.tests

import io.ktor.netty.*
import io.ktor.testing.*
import org.junit.*

class NettyHostTest : HostTestSuite<NettyApplicationHost>(Netty) {

    @Test
    @Ignore // runs too long
    override fun testBlockingDeadlock() {
        super.testBlockingDeadlock()
    }
}