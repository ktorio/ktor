/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.websocket

import io.ktor.server.netty.*

class NettyWebSocketTest : WebSocketEngineSuite<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty)
