/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*
import kotlin.test.*

class JettyBlockingServletContainerCompressionTest :
    CompressionTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false))

class JettyBlockingServletContainerContentTest :
    ContentTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false))

class JettyBlockingServletContainerHttpServerTest :
    HttpServerTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(Servlet(async = false)) {
    @Ignore
    override fun testUpgrade() {
    }
}

class JettyBlockingServletContainerSustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngineBase, JettyApplicationEngineBase.Configuration>(
        Servlet(async = false)
    )
