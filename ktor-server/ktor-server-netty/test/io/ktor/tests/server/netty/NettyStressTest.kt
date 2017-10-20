package io.ktor.tests.server.netty

import io.ktor.netty.*
import io.ktor.testing.*

class NettyStressTest : HostStressSuite<NettyApplicationHost, NettyApplicationHost.Configuration>(Netty) {
}
