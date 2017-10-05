package io.ktor.websocket

import io.ktor.jetty.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost>(Jetty)
