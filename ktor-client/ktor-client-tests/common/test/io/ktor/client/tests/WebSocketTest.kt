/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

@Suppress("PublicApiImplicitType")
class WebSocketTest : ClientLoader() {
    private val skipForWebsockets = listOf("Apache", "Android", "iOS", "Curl")

    @Test
    fun testEcho() = clientTests(skipForWebsockets) {
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
    fun testClose() = clientTests(skipForWebsockets) {
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
    fun testCancel() = clientTests(skipForWebsockets + "Js") {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<CancellationException> {
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
                assertTrue(frame is Frame.Text)
                assertEquals("PING", frame.readText())
            }
        }
    }

    @Test
    fun testConfiguration() = clientTests(skipForWebsockets) {
        config {
            WebSockets {
                pingInterval = 100
                maxFrameSize = 1024
            }
        }

        test { client ->
            assertEquals(100, client[WebSockets].pingInterval)
            assertEquals(1024, client[WebSockets].maxFrameSize)
        }
    }
}
