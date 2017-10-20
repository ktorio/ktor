package io.ktor.tests.websocket

import io.ktor.host.*
import io.ktor.tomcat.*

class TomcatWebSocketTest : WebSocketHostSuite<TomcatApplicationHost, TomcatApplicationHost.Configuration>(Tomcat)
