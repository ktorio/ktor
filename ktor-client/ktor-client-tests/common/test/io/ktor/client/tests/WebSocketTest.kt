/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.features.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

internal val ENGINES_WITHOUT_WEBSOCKETS = listOf("Apache", "Android", "iOS", "Curl", "native:CIO")
internal val ENGINES_WITHOUT_WS_EXTENSIONS = ENGINES_WITHOUT_WEBSOCKETS + "OkHttp" + "Java" + "Js"
private const val CUSTOM_HEADER = "X-Custom-Header"
private const val CUSTOM_HEADER_VALUE = "custom_header_value"

@Suppress("PublicApiImplicitType")
class WebSocketTest : ClientLoader() {
    @Test
    fun testEcho() = clientTests(ENGINES_WITHOUT_WEBSOCKETS) {
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
    @Ignore
    fun testClose() = clientTests(ENGINES_WITHOUT_WEBSOCKETS) {
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
    @Ignore
    fun testCancel() = clientTests(ENGINES_WITHOUT_WEBSOCKETS + "Js") {
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
    fun testEchoWSS() = clientTests(ENGINES_WITHOUT_WEBSOCKETS + "Js" + "native:CIO") {
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
    fun testWsHandshakeHeaders() = clientTests(ENGINES_WITHOUT_WEBSOCKETS + "native:CIO") {
        // browser websocket client does not support custom headers so the test gets ignored
        if (PlatformUtils.IS_BROWSER) return@clientTests
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession {
                url("$TEST_WEBSOCKET_SERVER/websockets/headers")
                header(CUSTOM_HEADER, CUSTOM_HEADER_VALUE)
            }

            val frame = session.incoming.receive()
            assertTrue(frame is Frame.Text)
            val headers =
                Json.decodeFromString<Map<String, List<String>>>(frame.readText())
            val header = headers[CUSTOM_HEADER]?.first()
            assertEquals(CUSTOM_HEADER_VALUE, header)
        }
    }

    @Test
    fun testWsHandshakeHeadersWithMultipleValues() = clientTests(ENGINES_WITHOUT_WEBSOCKETS + "native:CIO") {
        // browser websocket client does not support custom headers so the test gets ignored
        if (PlatformUtils.IS_BROWSER) return@clientTests
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession {
                url("$TEST_WEBSOCKET_SERVER/websockets/headers")
                header(CUSTOM_HEADER, CUSTOM_HEADER_VALUE)
                header(CUSTOM_HEADER, CUSTOM_HEADER_VALUE)
            }

            val frame = session.incoming.receive()
            assertTrue(frame is Frame.Text)
            val frameText = frame.readText()
            val headers = Json.decodeFromString<Map<String, List<String>>>(frameText)
            val header = headers[CUSTOM_HEADER]?.first()
            assertEquals("$CUSTOM_HEADER_VALUE,$CUSTOM_HEADER_VALUE", header)
        }
    }

    @Test
    fun testConfiguration() = clientTests(ENGINES_WITHOUT_WEBSOCKETS) {
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

    @OptIn(ExperimentalWebSocketExtensionApi::class)
    @Test
    fun testWebSocketExtensions() = clientTests(ENGINES_WITHOUT_WS_EXTENSIONS) {
        val testLogger = TestLogger(
            "Client negotiation",
            "Process outgoing frame: Frame TEXT (fin=true, buffer len = 12)",
            "Process incoming frame: Frame TEXT (fin=true, buffer len = 12)"
        )

        config {
            WebSockets {
                extensions {
                    install(FrameLogger) {
                        logger = testLogger
                    }
                }
            }
        }

        test { client ->
            client.ws("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                check(extensionOrNull(FrameLogger) != null)

                send("Hello, world")
                val frame = incoming.receive()
                assertEquals("Hello, world", (frame as Frame.Text).readText())
            }
        }

        after {
            testLogger.verify()
        }
    }

    @Test
    fun testExplicitClose() = clientTests(ENGINES_WITHOUT_WEBSOCKETS) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.ws("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                send(Frame.Text("Hello World"))
                delay(1000) // wait for server response
                close()

                var packetsCount = 0
                incoming.consumeEach {
                    val text = (it as? Frame.Text)?.readText() ?: return@consumeEach
                    assertEquals("Hello World", text)
                    packetsCount++
                }
                assertEquals(1, packetsCount)
            }
        }
    }
}
