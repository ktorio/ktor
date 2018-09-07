package io.ktor.tests.websocket

import io.ktor.server.jetty.*

class JettyWebSocketTest : WebSocketEngineSuite<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty)
