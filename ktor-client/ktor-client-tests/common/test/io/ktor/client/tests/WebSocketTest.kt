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

internal val ENGINES_WITHOUT_WEBSOCKETS = listOf("Apache", "Android", "iOS", "Curl", "native:CIO")
internal val ENGINES_WITHOUT_WS_EXTENSIONS = ENGINES_WITHOUT_WEBSOCKETS + "OkHttp" + "Java"

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

}
