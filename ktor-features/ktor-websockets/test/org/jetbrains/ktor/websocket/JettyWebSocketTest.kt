package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.jetty.*

class JettyWebSocketTest : WebSocketHostSuite<JettyApplicationHost>(Jetty)
