/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*

internal val ENGINES_WITHOUT_WS = listOf("Android", "Apache", "Curl")

private const val TEST_SIZE: Int = 100

class WebSocketTest : ClientLoader() {

    data class Data(val stringValue: String)

    private val customContentConverter = object : WebsocketContentConverter {
        override suspend fun serializeNullable(
            charset: Charset,
            typeInfo: TypeInfo,
            value: Any?
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
    fun testExceptionIfWebsocketIsNotInstalled() = testSuspend {
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
    fun testWebsocketSession() = clientTests(ENGINES_WITHOUT_WS) {
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
    fun testWebsocketWithDefaultRequest() = clientTests(ENGINES_WITHOUT_WS) {
        config {
            install(WebSockets)
            defaultRequest {
                val url = Url(TEST_WEBSOCKET_SERVER)
                host = url.host
                port = url.port
            }
        }

        test { client ->
            client.webSocket("/websockets/echo") {
                send("test")
                val response = incoming.receive() as Frame.Text
                assertEquals("test", response.readText())
            }
        }
    }

    @Test
    fun testWebsocketSessionWithError() = clientTests(ENGINES_WITHOUT_WS) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFails { client.webSocketSession("wss://testabcde.com/ws") }
        }
    }

    @Test
    fun testExceptionWss() = clientTests(listOf("Android", "Apache", "Curl", "JS")) {
        config {
            install(WebSockets)
        }

        test { client ->
            kotlin.test.assertFailsWith<IllegalStateException> {
                client.wss("$TEST_WEBSOCKET_SERVER/websockets/echo") { error("THIS IS AN ERROR !!!!") }
            }.let {
                assertEquals("THIS IS AN ERROR !!!!", it.message)
            }
        }
    }

    @Test
    fun testWebSocketSerialization() = clientTests(ENGINES_WITHOUT_WS) {
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
    fun testSerializationWithNoConverter() = clientTests(ENGINES_WITHOUT_WS) {
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

    @Test
    fun testQueryParameters() = clientTests(ENGINES_WITHOUT_WS) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.ws(
                host = "127.0.0.1",
                port = 8080,
                path = "/websockets/echo-query?param=hello"
            ) {
                val response = incoming.receive() as Frame.Text
                val query = response.readText()
                assertEquals("hello", query)
            }
        }
    }

    @Test
    fun testRequestTimeoutIsNotApplied() = clientTests(ENGINES_WITHOUT_WS) {
        config {
            install(WebSockets)

            install(HttpTimeout) {
                requestTimeoutMillis = 10
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                delay(20)

                send("test")
                val result = incoming.receive() as Frame.Text
                assertEquals("test", result.readText())
            }
        }
    }
}
