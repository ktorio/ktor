/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.suites.HooksTestSuite
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * Runs [HooksTestSuite] over HTTP/3.
 *
 * Covers application hook dispatch.
 */
class NettyHttp3HooksTest :
    HooksTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
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
