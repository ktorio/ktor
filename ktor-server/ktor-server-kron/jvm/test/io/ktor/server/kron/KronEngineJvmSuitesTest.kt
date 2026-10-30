/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.server.testing.suites.ConfigTestSuite
import io.ktor.server.testing.suites.ConnectionTestSuite
import io.ktor.server.testing.suites.ContentTestSuite
import io.ktor.server.testing.suites.HttpServerCommonTestSuite
import io.ktor.server.testing.suites.HttpServerJvmTestSuite
import io.ktor.server.testing.suites.ServerPluginsTestSuite
import io.ktor.server.testing.suites.SustainabilityTestSuite
import io.ktor.server.testing.suites.WebSocketEngineSuite
import kotlin.test.Ignore

// Ktor's engine suites, as the JVM engines run them, on kron's JVM target. HTTP/1.1 only: no TLS (kron's TLS is its
// own connector, which the suites cannot configure) and no HTTP/2. The tests kron does not pass that cannot be
// overridden are left out in the build script.

class KronJvmHttpServerCommonTest :
    HttpServerCommonTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class KronJvmWebSocketTest : WebSocketEngineSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}

class KronJvmHttpServerJvmTest :
    HttpServerJvmTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore // As for CIO
    override fun testPipeliningWithFlushingHeaders() {}
}

class KronJvmSustainabilityTest :
    SustainabilityTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore // As for CIO: a call's job descends from the server's parent job, not the application's
    override fun validateCallCoroutineContext() {}
}

class KronJvmContentTest : ContentTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Ignore // Glyph11 answers 400 to a CR or LF inside a quoted chunk extension, which RFC 9112 does not allow
    override fun funkyChunked() {}
}

class KronJvmConfigTest : ConfigTestSuite(Kron)

class KronJvmConnectionTest : ConnectionTestSuite(Kron)

class KronJvmPluginsTest : ServerPluginsTestSuite<KronApplicationEngine, KronApplicationEngine.Configuration>(Kron) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
