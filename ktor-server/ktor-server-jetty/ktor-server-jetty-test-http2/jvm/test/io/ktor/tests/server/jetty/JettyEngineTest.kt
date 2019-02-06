package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.*

class JettyEngineHttp2Test : EngineTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
