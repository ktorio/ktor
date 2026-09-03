/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.suites.ServerPluginsTestSuite
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * Runs [ServerPluginsTestSuite] over HTTP/3.
 *
 * Covers plugin pipeline behaviour.
 */
class NettyHttp3ServerPluginsTest :
    ServerPluginsTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        // HTTP/3 requires an SSL connector: it binds UDP on that connector's port.
        enableSsl = true
        enableHttp3 = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }
}
