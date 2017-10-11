package io.ktor.tests.websocket

import io.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost>(Tomcat)
