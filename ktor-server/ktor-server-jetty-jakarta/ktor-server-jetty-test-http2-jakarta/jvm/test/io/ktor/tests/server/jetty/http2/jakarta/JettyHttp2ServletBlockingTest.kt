/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.http2.jakarta

import io.ktor.server.jetty.jakarta.*
import io.ktor.server.testing.suites.*
import kotlin.test.Ignore
import kotlin.test.Test

class JettyHttp2BlockingServletContainerCompressionTest :
    CompressionTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false))

class JettyHttp2BlockingServletContainerContentTest :
    ContentTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false)) {
    @Ignore // KTOR-9263
    override fun funkyChunked() {
        super.funkyChunked()
    }
}

class JettyHttp2BlockingServletContainerHttpServerCommonTest :
    HttpServerCommonTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
        Servlet(async = false)
    ) {
    @Test
    @Http1Only // KTOR-10111: HTTP/2 responses still include Content-Length: 0 for 204.
    override fun `204 response has no content length or body`() {
        super.`204 response has no content length or body`()
    }

    override fun testFlushingHeaders() {
        // no op
    }
}

class JettyHttp2BlockingServletContainerHttpServerJvmTest :
    HttpServerJvmTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
        Servlet(async = false)
    ) {

    @Ignore
    override fun testUpgrade() {
    }

    @Ignore
    override fun testPipelining() {
    }

    @Ignore
    override fun testPipeliningWithFlushingHeaders() {
    }
}

class JettyHttp2BlockingServletContainerSustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
        Servlet(async = false)
    ) {
    @Ignore
    override fun validateCallCoroutineContext() {}
}
