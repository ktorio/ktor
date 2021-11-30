/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.netty

import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.suites.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.*
import kotlin.test.*
import io.ktor.utils.io.*
import java.net.*

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

class NettyHttpServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttpServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
        configuration.tcpKeepAlive = true
    }
}

class NettyHttp2ServerCommonTest :
    HttpServerCommonTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyHttp2ServerJvmTest :
    HttpServerJvmTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
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

class NettyConnectionTest : ConnectionTestSuite(Netty)

class NettyClientCertTest : ClientCertTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)

class NettyServerPluginsTest : ServerPluginsTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(
    Netty
) {
    init {
        enableSsl = false
        enableHttp2 = false
    }
}
