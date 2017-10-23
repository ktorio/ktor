package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.*

class NettyHostTest : HostTestSuite<NettyApplicationHost, NettyApplicationHost.Configuration>(Netty)