package io.ktor.tests.websocket

import io.ktor.server.jetty.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost, JettyApplicationHostBase.Configuration>(Jetty)
