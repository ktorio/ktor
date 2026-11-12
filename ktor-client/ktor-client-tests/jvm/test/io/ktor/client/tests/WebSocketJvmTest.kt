/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.plugins.websocket.*
import io.ktor.client.test.base.*
import io.ktor.client.test.base.EngineSelectionRule.Companion.except
import io.ktor.client.tests.utils.*
import io.ktor.websocket.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val TEST_SIZE: Int = 100

class WebSocketJvmTest : ClientLoader(except(ENGINES_WITHOUT_WS), timeout = 100000.seconds) {

    // Only CIO supports WebSocket extensions
    @Test
    fun testMaxFrameSizeIsCheckedAgainstCompressedSize() = clientTests(only("CIO")) {
        config {
            WebSockets {
                maxFrameSize = 100
                extensions {
                    install(WebSocketDeflateExtension)
                }
            }
        }

        test { client ->
            // Repeated characters shrink when compressed
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/text?size=101") {
                assertEquals(101, incoming.receive().data.size)
            }
            // Random data grows when compressed
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/random-binary?size=100") {
                assertFailsWith<FrameTooBigException> { incoming.receive() }
            }
        }
    }

    @Test
    fun testWebSocketDeflateBinary() = clientTests {
        config {
            WebSockets {
                extensions {
                    install(WebSocketDeflateExtension)
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) { size ->
                    val data = generateRandomByteArray(size, size * 10 + 1)
                    send(Frame.Binary(fin = true, data))

                    val actual = incoming.receive()
                    assertTrue(actual is Frame.Binary)
                    assertTrue { data.contentEquals(actual.data) }
                }
            }
        }
    }

    @Test
    fun testWebSocketDeflateNoContextTakeover() = clientTests {
        config {
            WebSockets {
                extensions {
                    install(WebSocketDeflateExtension) {
                        clientNoContextTakeOver = false
                        serverNoContextTakeOver = false
                    }
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) { size ->
                    val data = generateRandomByteArray(size, size * 10 + 1)
                    send(Frame.Binary(fin = true, data))

                    val actual = incoming.receive()
                    assertTrue(actual is Frame.Binary)
                    assertTrue { data.contentEquals(actual.data) }
                }
            }
        }
    }
}
