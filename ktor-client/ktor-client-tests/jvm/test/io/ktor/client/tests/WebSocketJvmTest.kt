/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

private const val TEST_SIZE: Int = 100

class WebSocketJvmTest : ClientLoader(100000) {
    data class Data(val stringValue: String)

    private val customContentConverter = object : WebsocketContentConverter {
        override suspend fun serialize(
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any
        ): Frame {
            if (value !is Data) return Frame.Text("")
            return Frame.Text("[${value.stringValue}]")
        }

        override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: Frame): Any {
            if (typeInfo.type != Data::class) {
                return Data("")
            }
            if (content !is Frame.Text) {
                return Data("")
            }
            return Data(content.readText().removeSurrounding("[", "]"))
        }

        override fun isApplicable(frame: Frame): Boolean {
            return frame is Frame.Text
        }
    }

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
    fun testExceptionIfWebsocketIsNotInstalled() = runBlocking {
        val client = HttpClient()
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.webSocketSession()
        }.let {
            assertContains(it.message!!, WebSockets.key.name)
        }
        kotlin.test.assertFailsWith<IllegalStateException> {
            client.webSocket {}
        }.let {
            assertContains(it.message!!, WebSockets.key.name)
        }
    }

    @Test
    fun testWebsocketSession() = clientTests(listOf("Android", "Apache")) {
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
            session.send("test")
            val response = session.incoming.receive() as Frame.Text
            assertEquals("test", response.readText())
            session.close()
        }
    }

    @Test
    fun testExceptionWss() = clientTests(listOf("Android", "Apache")) {
        config {
            install(WebSockets)
        }

        test { client ->
            kotlin.test.assertFailsWith<IllegalStateException> {
                client.wss("$TEST_WEBSOCKET_SERVER/websockets/echo") { error("error") }
            }.let {
                assertEquals("error", it.message)
            }
        }
    }

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

    @Test
    fun testWebSocketSerialization() = clientTests(listOf("Android", "Apache")) {
        config {
            WebSockets {
                contentConverter = customContentConverter
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) {
                    val originalData = Data("hello")
                    sendSerialized(originalData)
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
                assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                    sendSerialized(Data("hello"))
                }

                outgoing.send(Frame.Text("[hello]"))

                assertFailsWith<WebsocketConverterNotFoundException>("No converter was found for websocket") {
                    receiveDeserialized<Data>()
                }
            }
        }
    }
}
