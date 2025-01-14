/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp

import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinHttpWebSocketTests : ClientEngineTest<WinHttpClientEngineConfig>(WinHttp) {

    @Test
    fun testEcho() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text("Hello, world"))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("Hello, world", actual.readText())
            }
        }
    }

    @Test
    fun testEmptyFrame() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text(""))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("", actual.readText())
            }
        }
    }
}
