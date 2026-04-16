/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.engine.curl.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.websocket.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class CurlWebSocketTests : ClientEngineTest<CurlClientEngineConfig>(Curl, timeout = 10.seconds) {

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

    @Test
    fun testWebSocketHeaders() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
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

    @Test
    fun testParallelSessions() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
            val websocketInitialized = CompletableDeferred<Boolean>()

            launch {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                    websocketInitialized.complete(true)
                    delay(20)
                }
            }

            websocketInitialized.await()

            val response = client.get(TEST_SERVER)
            assertEquals(200, response.status.value)
        }
    }

    @Test
    fun testReceiveLargeTextFrame() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
            val payloadSize = 24000
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/text?size=$payloadSize") {
                val frame = incoming.receive()

                assertTrue(frame is Frame.Text, "Expected Frame.Text but got ${frame.frameType}")
                assertTrue(frame.fin, "Expected fin=true but got fin=false")

                val text = frame.readText()
                assertEquals(payloadSize, text.length, "Unexpected payload size")
            }
        }
    }
}
