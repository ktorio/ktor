/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.http2

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*
import kotlin.test.Ignore
import kotlin.test.Test

class JettyEngineHttp2CompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyEngineHttp2ContentTest :
    ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    @Ignore // KTOR-9263
    override fun funkyChunked() {
        super.funkyChunked()
    }
}

class JettyEngineHttp2HttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    @Test
    @Http1Only // KTOR-10111: HTTP/2 responses still include Content-Length: 0 for 204.
    override fun `204 response has no content length or body`() {
        super.`204 response has no content length or body`()
    }

    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyEngineHttp2HttpServerJvmTest :
    HttpServerJvmTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettyEngineHttp2SustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {

    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        configuration.callGroupSize = 5
    }

    @Ignore
    override fun validateCallCoroutineContext() {}
}

class JettyEngineServerPluginsTest :
    ServerPluginsTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
