/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class CurlWebSocketTests {

    private val TEST_WEBSOCKET_SERVER: String = "ws://127.0.0.1:8080"

    @Test
    fun testEcho() {
        val client = HttpClient(Curl) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text("Hello, world"))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("Hello, world", actual.readText())
            }
        }
    }

    @Test
    fun testEmptyFrame() {
        val client = HttpClient(Curl) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text(""))

                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)
                assertEquals("", actual.readText())
            }
        }
    }

    @Test
    fun testWebSocketHeaders() {
        val client = HttpClient(Curl) {
            install(WebSockets)
        }

        runBlocking {
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/headers") {
                val actual = incoming.receive()

                assertTrue(actual is Frame.Text)

                val headersString = actual.readText()
                val headers = Json.decodeFromString<Map<String, List<String>>>(headersString)

                assertEquals(listOf("Upgrade"), headers["Connection"])
                assertEquals(listOf("websocket"), headers["Upgrade"])
                assertEquals(listOf("13"), headers["Sec-WebSocket-Version"])
                val webSocketKey = assertNotNull(headers["Sec-WebSocket-Key"])
                assertTrue(webSocketKey.single().isNotEmpty())
            }
        }
    }
}
