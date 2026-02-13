/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.testing.suites.*
import kotlin.test.Ignore

class JettyCompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyJakartaContentTest : ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    @Ignore // KTOR-9263
    override fun funkyChunked() {
        super.funkyChunked()
    }
}

class JettyHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyHttpServerJvmTest : HttpServerJvmTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettySustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    // thread affinity is not accounted for in Jetty
    @Ignore
    override fun validateCallCoroutineContext() {}
}

class JettyConfigTest : ConfigTestSuite(Jetty)

class JettyConnectionTest : ConnectionTestSuite(Jetty)

class JettyServerPluginsTest : ServerPluginsTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(
    Jetty
) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
