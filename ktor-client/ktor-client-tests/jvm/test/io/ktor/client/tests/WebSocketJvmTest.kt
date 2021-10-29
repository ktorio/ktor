/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.cio.websocket.*
import kotlin.test.*

private const val TEST_SIZE: Int = 100

class WebSocketJvmTest : ClientLoader(100000) {

    @Test
    fun testWebSocketDeflateBinary() = clientTests(listOf("Android", "Apache")) {
        config {
            WebSockets {
                extensions {
                    install(WebSocketDeflateExtension)
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                client.plugin(WebSockets)

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
/*
    @Test
    fun testWebSocketDeflateNoContextTakeover() = clientTests(listOf("Android", "Apache")) {
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

    data class Data(val stringValue: String, val count: Int)

    @Test
    fun testWebSocketSerialization() = clientTests(listOf("Android", "Apache")) {
        config {
            WebSockets {
                contentConverter = GsonBaseConverter()
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) {
                    val originalData = Data("hello", 100)
                    sendSerializedByWebsocketConverter(originalData)
                    val actual = receiveDeserialized<Data>()
                    assertTrue { actual == originalData }
                }
            }
        }
    }

    @Test
    fun testSerializationWithNoConverter() = clientTests(listOf("Android", "Apache")) {
        config {
            WebSockets {
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) {
                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        sendSerializedByWebsocketConverter(Data("hello", 100))
                    }

                    outgoing.send(Frame.Text("{\"stringValue\":\"value\", \"count\":12}"))

                    assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                        receiveDeserialized<Data>()
                    }
                }
            }
        }
    }

 */
}
