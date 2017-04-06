package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost>(Tomcat)
