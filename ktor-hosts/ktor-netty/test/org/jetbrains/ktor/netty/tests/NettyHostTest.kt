package org.jetbrains.ktor.netty.tests

import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.testing.*
import org.junit.*

class NettyHostTest : HostTestSuite<NettyApplicationHost>(Netty) {

    @Ignore // runs too long
    override fun testBlockingDeadlock() {
        super.testBlockingDeadlock()
    }
}