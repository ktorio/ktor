package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.*

class JettyStressTest : HostStressSuite<JettyApplicationHost, JettyApplicationHostBase.Configuration>(Jetty)
