/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.suites.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class CIOHttpServerTest : HttpServerCommonTestSuite<CIOApplicationEngine, CIOApplicationEngine.Configuration>(CIO) {

    init {
        enableHttp2 = false
        enableSsl = false
    }

    @Test
    fun testGracefulShutdown() = runTest {
        val server = createAndStartServer {
            get("/") {
                delay(100.milliseconds)
                call.respond("OK")
            }
        }
        val body = CompletableDeferred<String?>()
        launch {
            withUrl("/") {
                body.complete(bodyAsText())
            }
        }
        launch {
            delay(20.milliseconds)
            server.stopSuspend(
                gracePeriodMillis = 10_000,
                timeoutMillis = 20_000,
            )
        }
        assertEquals("OK", body.await())
    }

    @Test
    fun testChunkedResponse() = runTest {
        createAndStartServer {
            get("/") {
                val byteStream = ByteChannel(autoFlush = true)
                byteStream.writeStringUtf8("test")
                byteStream.close(null)
                call.respond(object : OutgoingContent.ReadChannelContent() {
                    override val status: HttpStatusCode = HttpStatusCode.OK
                    override val headers: Headers = Headers.Empty
                    override fun readFrom() = byteStream
                })
            }
        }

        withUrl("/") {
            assertEquals("test", bodyAsText())
        }
    }

    @Test
    fun testExpectedContinue() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()
            val body = "Hello world"

            writePostHeaders(writeChannel, body.length)
            val continueResponse = readChannel.readLineStrict()
            assertEquals("HTTP/1.1 100 Continue", continueResponse)
            assertEquals("", readChannel.readLineStrict())

            writePostBody(writeChannel, body)
            val response = readAvailable(readChannel)
            assertTrue(response.contains("HTTP/1.1 200 OK"))
            assertTrue(response.contains(body))
        }
    }

    @Test
    fun testExpectedContinueRespondBeforeReadingBody() = runTest {
        createAndStartServer {
            post("/") {
                val length = call.request.headers[HttpHeaders.ContentLength]?.toInt() ?: 0
                if (length > 5) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val longBody = "Hello world"
            writePostHeaders(writeChannel, longBody.length)
            val badRequestResponse = readAvailable(readChannel)
            assertTrue(badRequestResponse.contains("HTTP/1.1 400 Bad Request"))
        }
    }

    @Test
    fun testExpectedContinueExpectationFailed() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respondText(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val longBody = "Hello world"
            writePostHeaders(writeChannel, longBody.length, expectedHeader = "invalid-100-continue")
            val expectationFailedResponse = readAvailable(readChannel)
            assertTrue(expectationFailedResponse.contains("HTTP/1.1 417 Expectation Failed"))
        }
    }

    @Test
    fun testExpectedContinueConnection() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respond(body)
            }
            post("/check-length") {
                val length = call.request.headers[HttpHeaders.ContentLength]?.toInt() ?: 0
                if (length == 0) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                call.respondText("ok")
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            writePostHeaders(writeChannel, path = "/check-length")
            val response = readAvailable(readChannel)
            assertTrue(response.contains("Connection: close"))
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            writePostHeaders(writeChannel, expectedHeader = "invalid")
            val response = readAvailable(readChannel)
            assertTrue(response.contains("Connection: close"))
        }
    }

    @Test
    fun testExpectedIgnoreHTTP1_0() = runTest {
        createAndStartServer {
            post("/") {
                val body = call.receiveText()
                call.respond(body)
            }
        }

        withClientSocket {
            val writeChannel = openWriteChannel()
            val readChannel = openReadChannel()

            val body = "Hello world"
            writePostHeaders(writeChannel, body.length, httpVersion = "HTTP/1.0")
            writePostBody(writeChannel, body)
            val response = readAvailable(readChannel)
            assertFalse(response.contains("100 Continue"))
        }
    }

    @Test
    fun testLotsOfHeaders() = runTest {
        val count = 500
        val implicitHeadersCount = 3

        createAndStartServer {
            get("/headers") {
                call.respond("${call.request.headers.entries().size} headers received")
            }
        }
        withUrl("/headers", {
            repeat(count) {
                header("HeaderName$it", "HeaderContent$it")
            }
        }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("${count + implicitHeadersCount} headers received", bodyAsText())
        }
    }

    @Test
    fun `duplicate Transfer-Encoding header is rejected and connection is closed`() = runTest {
        var adminInvoked = false
        createAndStartServer {
            post("/") {
                call.respondText(call.receiveText())
            }
            get("/admin") {
                adminInvoked = true
                call.respondText("secret")
            }
        }

        withClientSocket {
            val smuggled = "GET /admin HTTP/1.1\r\nHost: $TEST_SERVER_HOST\r\n\r\n"
            val body = "0\r\n\r\n$smuggled"
            val response = sendRaw(
                "POST / HTTP/1.1\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Transfer-Encoding: identity\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "\r\n" +
                    body
            )
            assertSingleBadRequest(response, "Duplicate Transfer-Encoding header")
        }
        assertFalse(adminInvoked, "smuggled request must not reach the application")
    }

    @Test
    fun `Transfer-Encoding with Content-Length is rejected`() = runTest {
        createAndStartServer {
            post("/") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST / HTTP/1.1\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Content-Length: 5\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n0\r\n\r\n"
            )
            assertSingleBadRequest(response, "Transfer-Encoding and Content-Length headers must not be sent together")
        }
    }

    @Test
    fun `Transfer-Encoding list ending with identity is rejected`() = runTest {
        createAndStartServer {
            post("/") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST / HTTP/1.1\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: chunked, identity\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n0\r\n\r\n"
            )
            assertSingleBadRequest(response, "Unsupported Transfer-Encoding")
        }
    }

    @Test
    fun `Transfer-Encoding identity is rejected`() = runTest {
        createAndStartServer {
            post("/") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST / HTTP/1.1\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: identity\r\n" +
                    "\r\n" +
                    "hello"
            )
            assertSingleBadRequest(response, "Unsupported Transfer-Encoding")
        }
    }

    @Test
    fun `Transfer-Encoding on HTTP 1_0 request is rejected`() = runTest {
        createAndStartServer {
            post("/") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST / HTTP/1.0\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n0\r\n\r\n"
            )
            assertSingleBadRequest(response, "Transfer-Encoding header is not allowed in HTTP/1.0 requests")
        }
    }

    @Test
    fun `HTTP 1_0 request with Content-Length is still accepted`() = runTest {
        createAndStartServer {
            post("/echo") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST /echo HTTP/1.0\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Content-Length: 5\r\n" +
                    "\r\n" +
                    "hello"
            )
            assertTrue(response.contains(" 200 OK"), response)
            assertTrue(response.endsWith("hello"), response)
        }
    }

    @Test
    fun `chunked request body is still accepted`() = runTest {
        createAndStartServer {
            post("/echo") {
                call.respondText(call.receiveText())
            }
        }

        withClientSocket {
            val response = sendRaw(
                "POST /echo HTTP/1.1\r\n" +
                    "Host: $TEST_SERVER_HOST\r\n" +
                    "Transfer-Encoding: chunked\r\n" +
                    "Connection: close\r\n" +
                    "\r\n" +
                    "5\r\nhello\r\n0\r\n\r\n"
            )
            assertTrue(response.startsWith("HTTP/1.1 200 OK"), response)
            assertTrue(response.endsWith("hello"), response)
        }
    }

    /**
     * Writes [request] and reads until the server closes the connection.
     * Relies on the test timeout to fail if the connection is kept open.
     */
    private suspend fun Socket.sendRaw(request: String): String {
        val writeChannel = openWriteChannel()
        val readChannel = openReadChannel()
        writeChannel.writeStringUtf8(request)
        writeChannel.flush()
        return readChannel.toByteArray().decodeToString()
    }

    private fun assertSingleBadRequest(response: String, message: String) {
        assertTrue(response.startsWith("HTTP/1.0 400 Bad Request"), response)
        assertTrue(response.contains("Connection: close"), response)
        assertTrue(response.contains(message), response)
        val responseCount = response.lines().count { it.startsWith("HTTP/1.") }
        assertEquals(1, responseCount, "Expected exactly one response, got:\n$response")
    }

    private suspend fun readAvailable(channel: ByteReadChannel): String {
        val buffer = ByteArray(1024)
        val length = channel.readAvailable(buffer)
        return buffer.decodeToString(0, 0 + length)
    }

    private suspend fun withClientSocket(block: suspend Socket.() -> Unit) {
        SelectorManager().use {
            aSocket(it).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                block(socket)
            }
        }
    }

    private suspend fun writePostHeaders(
        channel: ByteWriteChannel,
        length: Int = 0,
        path: String = "/",
        expectedHeader: String = "100-continue",
        httpVersion: String = "HTTP/1.1"
    ) {
        channel.apply {
            writeStringUtf8("POST $path $httpVersion\r\n")
            writeStringUtf8("Host: $TEST_SERVER_HOST\r\n")
            writeStringUtf8("Content-Type: text/plain\r\n")
            writeStringUtf8("Content-Length: $length\r\n")
            writeStringUtf8("Expect: $expectedHeader\r\n")
            writeStringUtf8("Connection: close\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private suspend fun writePostBody(channel: ByteWriteChannel, body: String) {
        channel.apply {
            writeStringUtf8("$body\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
    }
}
