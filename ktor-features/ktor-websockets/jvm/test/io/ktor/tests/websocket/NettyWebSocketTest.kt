package io.ktor.tests.websocket

import io.ktor.server.netty.*

class NettyWebSocketTest : WebSocketEngineSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)
