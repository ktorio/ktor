package io.ktor.websocket

import io.ktor.netty.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost>(Netty)