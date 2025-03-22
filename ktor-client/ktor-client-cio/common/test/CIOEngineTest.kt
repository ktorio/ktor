/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class CIOEngineTest : ClientEngineTest<CIOEngineConfig>(CIO) {

    @Test
    fun testRequestTimeoutIgnoredWithWebSocket() = testClient {
        config {
            engine {
                requestTimeout = 10
            }

            install(WebSockets)
        }

        test { client ->
            var received = false
            client.ws("$TEST_WEBSOCKET_SERVER/websockets/echo") {
                delay(20)

                send(Frame.Text("Hello"))

                val response = incoming.receive() as Frame.Text
                received = true
                assertEquals("Hello", response.readText())
            }

            assertTrue(received)
        }
    }

    @Test
    fun testRequestTimeoutIgnoredWithSSE() = testClient {
        config {
            engine {
                requestTimeout = 10
            }

            install(SSE)
        }

        test { client ->
            var received = false
            client.sse("$TEST_SERVER/sse/hello?delay=20") {
                val response = incoming.single()
                received = true
                assertEquals("hello\r\nfrom server", response.data)
            }
            assertTrue(received)
        }
    }

    @Test
    fun testExpectHeader() = testClient {
        val body = "Hello World"

        withServerSocket { client, socket ->
            sendExpectRequest(socket, client, body) {
                assertEquals(HttpStatusCode.OK, status)
            }

            socket.accept().use {
                val readChannel = it.openReadChannel()
                val writeChannel = it.openWriteChannel()

                val headers = readAvailableLines(readChannel)
                assertTrue(headers.contains(EXPECT_HEADER))
                assertFalse(headers.contains(body))

                writeContinueResponse(writeChannel)
                val actualBody = readAvailableLine(readChannel)
                assertEquals(body, actualBody)
                writeOkResponse(writeChannel)
            }
        }
    }

    @Test
    fun testNoExpectHeaderIfNoBody() = testClient {
        withServerSocket { client, socket ->
            sendExpectRequest(socket, client) {
                assertEquals(HttpStatusCode.OK, status)
            }

            socket.accept().use {
                val readChannel = it.openReadChannel()
                val writeChannel = it.openWriteChannel()

                val headers = readAvailableLines(readChannel)
                assertFalse(headers.contains(EXPECT_HEADER))
                writeOkResponse(writeChannel)
            }
        }
    }

    @Test
    fun testDontWaitForContinueResponse() = testClient(timeout = 30.seconds) {
        config {
            engine {
                requestTimeout = 0
            }
        }

        val body = "Hello World\n"

        withServerSocket { client, socket ->
            sendExpectRequest(socket, client, body) {
                assertEquals(HttpStatusCode.OK, status)
            }

            socket.accept().use {
                val readChannel = it.openReadChannel()
                val writeChannel = it.openWriteChannel()

                val headers = readAvailableLines(readChannel)
                delay(2000)
                val actualBody = readAvailableLine(readChannel)
                assertTrue(headers.contains(EXPECT_HEADER))
                assertEquals(body, actualBody)
                writeOkResponse(writeChannel)
            }
        }
    }

    @Test
    fun testRepeatRequestAfterExpectationFailed() = testClient {
        val body = "Hello World"

        withServerSocket { client, socket ->
            sendExpectRequest(socket, client, body) {
                assertEquals(HttpStatusCode.OK, status)
            }

            socket.accept().use {
                val readChannel = it.openReadChannel()
                val writeChannel = it.openWriteChannel()

                val headers = readAvailableLines(readChannel)
                assertTrue(headers.contains(EXPECT_HEADER))
                writeExpectationFailedResponse(writeChannel)

                delay(100) // because channel.flush() happens between writing headers and body
                val newRequest = readAvailableLines(readChannel)
                assertFalse(newRequest.contains(EXPECT_HEADER))
                assertTrue(newRequest.contains(body))
                writeOkResponse(writeChannel)
            }
        }
    }

    @Test
    fun testErrorMessageWhenServerDontRespondWithUpgrade() = testClient {
        config {
            install(WebSockets)
        }

        test { client ->
            assertFailsWith<WebSocketException> {
                client.webSocket("$TEST_WEBSOCKET_SERVER/websockets/500") {}
            }.apply {
                assertEquals(message, "Handshake exception, expected status code 101 but was 500")
            }
        }
    }

    private fun CoroutineScope.sendExpectRequest(
        socket: ServerSocket,
        client: HttpClient,
        body: String? = null,
        block: HttpResponse.() -> Unit = {}
    ) {
        launch {
            client.post {
                val serverPort = (socket.localAddress as InetSocketAddress).port
                url(host = TEST_SERVER_SOCKET_HOST, port = serverPort, path = "/")
                header(HttpHeaders.Expect, "100-continue")
                if (body != null) setBody(body)
            }.apply(block)
        }
    }

    private suspend fun readAvailableLine(channel: ByteReadChannel): String {
        val buffer = ByteArray(1024)
        val length = channel.readAvailable(buffer)
        return buffer.decodeToString(0, 0 + length)
    }

    private suspend fun readAvailableLines(channel: ByteReadChannel): List<String> {
        return readAvailableLine(channel).split("\r\n")
    }

    private suspend fun writeContinueResponse(channel: ByteWriteChannel) {
        channel.apply {
            writeStringUtf8("HTTP/1.1 100 Continue\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private suspend fun writeOkResponse(channel: ByteWriteChannel) {
        channel.apply {
            writeStringUtf8("HTTP/1.1 200 Ok\r\n")
            writeStringUtf8("Content-Length: 0\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private suspend fun writeExpectationFailedResponse(channel: ByteWriteChannel) {
        channel.apply {
            writeStringUtf8("HTTP/1.1 417 Expectation Failed\r\n")
            writeStringUtf8("Content-Length: 0\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private fun TestClientBuilder<*>.withServerSocket(
        block: suspend CoroutineScope.(HttpClient, ServerSocket) -> Unit,
    ) = test { client ->
        val selectorManager = SelectorManager()
        selectorManager.use {
            aSocket(it).tcp().bind(TEST_SERVER_SOCKET_HOST, 0).use { socket ->
                coroutineScope {
                    block(client, socket)
                }
            }
        }
    }

    companion object {
        private const val TEST_SERVER_SOCKET_HOST = "127.0.0.1"
        private const val EXPECT_HEADER = "Expect: 100-continue"
    }
}
