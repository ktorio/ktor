package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.*

class JettyStressTest : EngineStressSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
