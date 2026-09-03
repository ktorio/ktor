/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.testing.suites.SustainabilityTestSuite
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * Runs [SustainabilityTestSuite] over HTTP/3.
 *
 * Covers failure handling under load: exceptions in interceptors, client disconnects and
 * jobs cancelled on shutdown.
 */
class NettyHttp3SustainabilityTest :
    SustainabilityTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
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
