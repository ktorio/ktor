/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty.http2

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class JettyEngineHttp2CompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyEngineHttp2ContentTest :
    ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyEngineHttp2HttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
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
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyEngineServerPluginsTest :
    ServerPluginsTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
