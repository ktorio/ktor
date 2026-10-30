/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.server.testing.suites.HttpServerCommonTestSuite
import io.ktor.server.testing.suites.WebSocketEngineSuite

class KronHttpServerTest : HttpServerCommonTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class KronWebSocketTest : WebSocketEngineSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron)
