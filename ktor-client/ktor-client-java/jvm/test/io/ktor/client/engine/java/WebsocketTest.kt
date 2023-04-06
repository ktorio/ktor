/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class WebsocketTest {
    @Test
    fun testCountPong() {
        val client = HttpClient(Java) {
            install(WebSockets)
        }

        runBlocking {
            client.ws("$TEST_WEBSOCKET_SERVER/websockets/count-pong") {
                outgoing.send(Frame.Text("get pong count"))
                val countOfPongFrame = incoming.receive()
                check(countOfPongFrame is Frame.Text)
                assertEquals("1", countOfPongFrame.readText())
            }
        }
    }
}
