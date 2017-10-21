package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.*

class NettyStressTest : HostStressSuite<NettyApplicationHost, NettyApplicationHost.Configuration>(Netty) {
}
