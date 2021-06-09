/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class NettyCompressionTest : CompressionTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyContentTest : ContentTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyHttpServerTest : HttpServerTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.tcpKeepAlive = true
    }
}

class NettyHttp2ServerTest : HttpServerTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
        enableHttp2 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettySustainabilityTest : SustainabilityTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}

class NettyConfigTest : ConfigTestSuite(Netty)
