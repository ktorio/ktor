package io.ktor.tests.websocket

import io.ktor.netty.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost>(Netty)