package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.*

class JettyEngineTest : EngineTestSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
