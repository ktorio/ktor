package io.ktor.tests.websocket

import io.ktor.jetty.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost>(Jetty)
