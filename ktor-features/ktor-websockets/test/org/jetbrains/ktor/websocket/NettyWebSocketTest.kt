package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.netty.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost>(Netty)