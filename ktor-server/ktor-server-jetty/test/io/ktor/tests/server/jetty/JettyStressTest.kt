package io.ktor.tests.server.jetty

import io.ktor.jetty.*
import io.ktor.testing.*

class JettyStressTest : HostStressSuite<JettyApplicationHost>(Jetty)
