package io.ktor.tests.websocket

import io.ktor.jetty.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost, JettyApplicationHostBase.Configuration>(Jetty)
