/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.test.dispatcher.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.charsets.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

internal val ENGINES_WITHOUT_WS = listOf("Android", "Apache", "Apache5", "Curl", "DarwinLegacy")

private const val TEST_SIZE: Int = 100

class WebSocketTest : ClientLoader() {

    data class Data(val stringValue: String)

    private val customContentConverter = object : WebsocketContentConverter {
        override suspend fun serialize(
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
        assertFailsWith<IllegalStateException> {
            client.webSocketSession()
        }.let {
            assertContains(it.message!!, WebSockets.key.name)
        }
        assertFailsWith<IllegalStateException> {
            client.webSocket {}
        }.let {
            assertContains(it.message!!, WebSockets.key.name)
        }
    }

    @Test
    fun testWebsocketSession() = clientTests(except(ENGINES_WITHOUT_WS)) {
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
    fun testParallelWebsocketSessions() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            coroutineScope {
                launch {
                    val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
                    repeat(100) {
                        session.send("test 1 $it")
                        val response = session.incoming.receive() as Frame.Text
                        assertEquals("test 1 $it", response.readText())
                    }
                    session.close()
                }
                launch {
                    val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
                    repeat(100) {
                        session.send("test 2 $it")
                        val response = session.incoming.receive() as Frame.Text
                        assertEquals("test 2 $it", response.readText())
                    }
                    session.close()
                }
            }
        }
    }

    @Test
    fun testWebsocketWithDefaultRequest() = clientTests(except(ENGINES_WITHOUT_WS + "Js")) {
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
    fun testWebsocketSessionWithError() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFails { client.webSocketSession("wss://testabcde.com/ws") }
        }
    }

    @Test
    fun testExceptionWss() = clientTests(except(ENGINES_WITHOUT_WS + "Js")) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<IllegalStateException> {
                client.wss("$TEST_WEBSOCKET_SERVER/websockets/echo") { error("THIS IS AN ERROR !!!!") }
            }.let {
                assertEquals("THIS IS AN ERROR !!!!", it.message)
            }
        }
    }

    @Test
    fun testWebSocketSerialization() = clientTests(except(ENGINES_WITHOUT_WS)) {
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
    fun testWebSocketSerializationWithExplicitTypeInfo() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            WebSockets {
                contentConverter = customContentConverter
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(TEST_SIZE) {
                    val originalData = Data("hello")
                    sendSerialized(originalData, typeInfo<Data>())
                    val actual = receiveDeserialized<Data>(typeInfo<Data>())
                    assertTrue { actual == originalData }
                }
            }
        }
    }

    @Test
    fun testSerializationWithNoConverter() = clientTests(except(ENGINES_WITHOUT_WS)) {
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
    fun testRequestTimeoutIsNotApplied() = clientTests(except(ENGINES_WITHOUT_WS)) {
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

    @Test
    fun testCountPong() = clientTests(except(ENGINES_WITHOUT_WS + "Js")) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/count-pong") {
                delay(100)
                send("count pong")
                val countOfPongFrame = incoming.receive() as Frame.Text
                assertEquals("1", countOfPongFrame.readText())
            }
        }
    }

    @Test
    fun testCancellingScope() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/echo")
            session.send("test")
            val result = session.incoming.receive() as Frame.Text
            assertEquals("test", result.readText())

            client.coroutineContext[Job]!!.cancel("test", IllegalStateException("test"))
            assertNotNull(session.closeReason.await())
        }
    }

    @Test
    fun testWebsocketRequiringSubProtocolWithSubProtocol() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket(
                "$TEST_WEBSOCKET_SERVER/websockets/sub-protocol",
                request = {
                    header(HttpHeaders.SecWebSocketProtocol, "test-protocol")
                }
            ) {
                send(Frame.Text("test"))
                val result = incoming.receive() as Frame.Text
                assertEquals("test", result.readText())
            }
        }
    }

    @Test
    fun testWebsocketRequiringSubProtocolWithoutSubProtocol() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<Exception> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/sub-protocol") {
                    send(Frame.Text("test"))
                }
            }
        }
    }

    @Test
    fun testResponseContainsSecWebsocketProtocolHeader() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            val session = client.webSocketSession("$TEST_WEBSOCKET_SERVER/websockets/sub-protocol") {
                header(HttpHeaders.SecWebSocketProtocol, "test-protocol")
            }

            try {
                assertEquals(session.call.response.headers[HttpHeaders.SecWebSocketProtocol], "test-protocol")
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun testIncomingOverflow() = clientTests(except(ENGINES_WITHOUT_WS)) {
        config {
            install(WebSockets)
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                repeat(1000) {
                    send("test")
                }
            }
        }
    }

    @Test
    fun testImmediateReceiveAfterConnect() = clientTests(
        except(ENGINES_WITHOUT_WS + "Darwin"), // TODO KTOR-7088
    ) {
        config {
            install(WebSockets)
        }

        test { client ->
            coroutineScope {
                val defs = (1..100).map {
                    async {
                        client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/headers") {
                            val frame = withTimeoutOrNull(1.seconds) { incoming.receive() }
                            assertNotNull(frame)
                            assertIs<Frame.Text>(frame)
                        }
                    }
                }
                defs.awaitAll()
            }
        }
    }

    @Test
    fun testAuthenticationWithValidRefreshToken() = clientTests(except(ENGINES_WITHOUT_WS + "Js" + "WinHttp")) {
        config {
            install(WebSockets)

            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("invalid", "invalid") }
                    refreshTokens { BearerTokens("valid", "valid") }
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/auth/websocket") {
                val frame = incoming.receive() as Frame.Text
                assertEquals("Hello from server", frame.readText())
            }
            client.close()
        }
    }

    @Test
    fun testAuthenticationWithValidInitialToken() = clientTests(
        except(ENGINES_WITHOUT_WS + "Js" + "Darwin"),
        retries = 5
    ) {
        config {
            install(WebSockets)

            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("valid", "valid") }
                }
            }
        }

        test { client ->
            client.webSocket("$TEST_WEBSOCKET_SERVER/auth/websocket") {
                val frame = incoming.receive() as Frame.Text
                assertEquals("Hello from server", frame.readText())
            }
            client.close()
        }
    }

    @Test
    fun testAuthenticationWithInvalidToken() = clientTests(except(ENGINES_WITHOUT_WS + "Js" + "WinHttp")) {
        config {
            install(WebSockets)

            install(Auth) {
                bearer {
                    loadTokens { BearerTokens("invalid", "invalid") }
                    refreshTokens { BearerTokens("invalid", "invalid") }
                }
            }
        }

        test { client ->
            assertFailsWith<WebSocketException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/auth/websocket") {}
            }
            client.close()
        }
    }
}
