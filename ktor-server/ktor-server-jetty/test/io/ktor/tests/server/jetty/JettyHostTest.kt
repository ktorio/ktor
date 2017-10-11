package io.ktor.tests.server.jetty

import io.ktor.jetty.*
import io.ktor.testing.*

class JettyHostTest : HostTestSuite<JettyApplicationHost>(Jetty)