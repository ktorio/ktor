/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.server.netty.*
import io.ktor.server.testing.*

class NettyEngineTest : EngineTestSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {
    init {
        enableSsl = true
    }
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.shareWorkGroup = true
    }
}
