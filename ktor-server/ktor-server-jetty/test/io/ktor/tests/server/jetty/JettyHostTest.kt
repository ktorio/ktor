package io.ktor.tests.server.jetty

import io.ktor.server.jetty.*
import io.ktor.server.testing.*

class JettyHostTest : HostTestSuite<JettyApplicationHost, JettyApplicationHostBase.Configuration>(Jetty)