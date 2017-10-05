package io.ktor.websocket

import io.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost>(Tomcat)
