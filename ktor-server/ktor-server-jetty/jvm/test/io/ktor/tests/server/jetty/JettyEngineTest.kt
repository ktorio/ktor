/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.suites.*

class JettyCompressionTest :
    CompressionTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyContentTest : ContentTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettyHttpServerTest : HttpServerTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)

class JettySustainabilityTest :
    SustainabilityTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
