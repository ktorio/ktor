package io.ktor.tests.websocket

import io.ktor.server.tomcat.*

class TomcatWebSocketTest : WebSocketEngineSuite<TomcatApplicationEngine, TomcatApplicationEngine.Configuration>(Tomcat)
