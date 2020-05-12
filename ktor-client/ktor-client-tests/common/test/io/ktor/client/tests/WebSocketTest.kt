/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

class WebSocketTest : ClientLoader() {

    @Test
    fun testEcho() = clientTests(listOf("Apache", "Android", "iOS")) {
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
    fun testClose() = clientTests(listOf("Apache", "Android", "iOS")) {
        config {
            install(WebSockets)
        }

        test { client ->
            withTimeout(2000) {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/close") {
                    send(Frame.Text("End"))
                    val closeReason = closeReason.await()!!
                    assertEquals("End", closeReason.message)
                    assertEquals(1000, closeReason.code)
                }
            }
        }
    }

    @Test
    fun testCancel() = clientTests(listOf("Apache", "Android", "Js", "iOS")) {
        config {
            install(WebSockets)
        }

        test { client ->
            io.ktor.client.tests.utils.assertFailsWith<CancellationException> {
                withTimeout(1000) {
                    client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                        repeat(10) {
                            send(Frame.Text("Hello"))
                            delay(250)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testEchoWSS() = clientTests(listOf("Apache", "Android", "Js", "iOS")) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket("wss://echo.websocket.org") {
                outgoing.send(Frame.Text("PING"))
                val frame = incoming.receive()
                if (frame is Frame.Text) {
                    assertEquals("PING", frame.readText())
                }
            }
        }
    }
}
