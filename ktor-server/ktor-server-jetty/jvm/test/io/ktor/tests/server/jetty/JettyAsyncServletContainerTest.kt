/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class JettyAsyncServletContainerCompressionTest :
    CompressionTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(Servlet(async = true))

class JettyAsyncServletContainerContentTest :
    ContentTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(Servlet(async = true))

class JettyAsyncServletContainerHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(
        Servlet(async = true)
    ) {
    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyAsyncServletContainerHttpServerJvmTest :
    HttpServerJvmTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(
        Servlet(async = true)
    ) {
    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettyAsyncServletContainerSustainabilityTest :
    SustainabilityTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(Servlet(async = true))

class JettyAsyncServerPluginsTest :
    ServerPluginsTestSuite<JettyServerEngineBase, JettyServerEngineBase.Configuration>(
        Servlet(async = true)
    ) {
    init {
        enableHttp2 = false
        enableSsl = false
    }
}
