package io.ktor.tests.websocket

import io.ktor.server.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost, TomcatApplicationHost.Configuration>(Tomcat)
