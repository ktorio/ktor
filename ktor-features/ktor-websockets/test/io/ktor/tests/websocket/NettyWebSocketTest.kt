package io.ktor.tests.websocket

import io.ktor.server.netty.*

class NettyWebSocketTest : WebSocketHostSuite<NettyApplicationHost, NettyApplicationHost.Configuration>(Netty)