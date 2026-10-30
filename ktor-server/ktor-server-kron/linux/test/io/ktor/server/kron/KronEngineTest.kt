/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.discard
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class KronEngineTest {
    private lateinit var server: TestServer

    private val app: suspend Application.() -> Unit = {
        routing {
            get("/") { call.respondText("hello") }
            get("/query") {
                call.respondText(
                    "${call.request.queryParameters["a"]}|${call.request.queryParameters["b"]}|${call.request.queryParameters["c"]}"
                )
            }
            get("/users/{id}") { call.respondText("user ${call.parameters["id"]}") }
            get("/header") {
                call.response.header("X-Echo", call.request.header("X-Test") ?: "none")
                call.respondText(call.request.headers.getAll("X-Many")?.joinToString(",") ?: "")
            }
            post("/echo") { call.respondText(call.receiveText()) }
            post("/count") { call.respondText(call.receiveChannel().discard().toString()) }
            post("/ignore") { call.respondText("ignored") }
            get("/stream") {
                call.respondBytesWriter(ContentType.Text.Plain) {
                    writeStringUtf8("first,")
                    flush()
                    writeStringUtf8("second")
                }
            }
            get("/big") { call.respondBytes(ByteArray(BIG) { (it % 251).toByte() }) }
            get("/fail") { throw IllegalStateException("boom") }
            get("/delay/{ms}") {
                val ms = call.parameters["ms"]!!.toLong()
                delay(ms)
                call.respondText(ms.toString())
            }
            get("/timeout") {
                call.respondText(
                    withTimeoutOrNull(20) {
                        delay(10_000)
                        "late"
                    } ?: "timed out"
                )
            }
            get("/launch") {
                call.launch { throw IllegalStateException("a coroutine the route left failing") }
                call.respondText("launched")
            }
            get("/status") { call.respondText("made", status = HttpStatusCode.Created) }
            head("/only-head") { call.respondText("not sent") }
        }
    }

    @BeforeTest
    fun start() {
        server = TestServer(app)
    }

    @AfterTest
    fun stop() {
        server.close()
    }

    @Test
    fun `a GET is answered with its text and length`() = server.connect().use { client ->
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        val response = client.readResponse()
        assertEquals(200, response.status)
        assertEquals("hello", response.text)
        assertEquals("5", response.header("Content-Length"))
        assertTrue(response.header("Content-Type")!!.startsWith("text/plain"))
    }

    @Test
    fun `an unknown path is 404 and the connection stays open`() = server.connect().use { client ->
        client.write("GET /nowhere HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals(404, client.readResponse().status)
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `query and path parameters reach the route decoded`() = server.connect().use { client ->
        client.write("GET /query?a=1&b=two%20words&c HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("1|two words|", client.readResponse().text)
        client.write("GET /users/42 HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("user 42", client.readResponse().text)
    }

    @Test
    fun `request headers are read case-insensitively and response headers are sent`() = server.connect().use { client ->
        client.write("GET /header HTTP/1.1\r\nHost: x\r\nx-test: abc  \r\nX-Many: 1\r\nx-many: 2\r\n\r\n")
        val response = client.readResponse()
        assertEquals("abc", response.header("X-Echo"))
        assertEquals("1,2", response.text)
    }

    @Test
    fun `a Content-Length body is echoed`() = server.connect().use { client ->
        client.write("POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 11\r\n\r\nhello world")
        assertEquals("hello world", client.readResponse().text)
    }

    @Test
    fun `a chunked body is decoded`() = server.connect().use { client ->
        client.write(
            "POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"
        )
        assertEquals("hello world", client.readResponse().text)
    }

    @Test
    fun `a body that arrives in pieces is read whole`() = server.connect().use { client ->
        client.write("POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 10\r\n\r\n01234")
        platform.posix.usleep(50_000u)
        client.write("56789")
        assertEquals("0123456789", client.readResponse().text)

        client.write("POST /echo HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc")
        platform.posix.usleep(50_000u)
        client.write("\r\n2\r\nde\r\n0\r\n")
        platform.posix.usleep(50_000u)
        client.write("\r\n")
        assertEquals("abcde", client.readResponse().text)
    }

    // The first piece leaves the 4 KiB carry nearly full; the second only fits once what was read is dropped from its
    // front, and nothing comes after it to wake a reader that missed it.
    @Test
    fun `a body whose last piece moves the received bytes to the front is read whole`() {
        server.connect().use { client ->
            client.write("POST /count HTTP/1.1\r\nHost: x\r\nContent-Length: 5000\r\n\r\n" + "a".repeat(3000))
            platform.posix.usleep(50_000u)
            client.write("b".repeat(2000))
            assertEquals("5000", client.readResponse().text)
        }
    }

    @Test
    fun `a large body streams through`() = server.connect().use { client ->
        val size = 4 * 1024 * 1024
        client.write("POST /count HTTP/1.1\r\nHost: x\r\nContent-Length: $size\r\n\r\n")
        client.write(ByteArray(size) { 'x'.code.toByte() })
        assertEquals(size.toString(), client.readResponse().text)
    }

    @Test
    fun `a body the route never reads is skipped and the next request is served`() = server.connect().use { client ->
        client.write(
            "POST /ignore HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nabcdeGET / HTTP/1.1\r\nHost: x\r\n\r\n"
        )
        assertEquals("ignored", client.readResponse().text)
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `pipelined requests are answered in order`() = server.connect().use { client ->
        client.write(
            "GET / HTTP/1.1\r\nHost: x\r\n\r\n" +
                "POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 3\r\n\r\nabc" +
                "GET /users/7 HTTP/1.1\r\nHost: x\r\n\r\n",
        )
        assertEquals("hello", client.readResponse().text)
        assertEquals("abc", client.readResponse().text)
        assertEquals("user 7", client.readResponse().text)
    }

    @Test
    fun `a response is not held back while the next request's body has not arrived`() = server.connect().use { client ->
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\nPOST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
        client.write("ping")
        assertEquals("ping", client.readResponse().text)
    }

    @Test
    fun `a streamed response is sent chunked`() = server.connect().use { client ->
        client.write("GET /stream HTTP/1.1\r\nHost: x\r\n\r\n")
        val response = client.readResponse()
        assertEquals("chunked", response.header("Transfer-Encoding"))
        assertEquals("first,second", response.text)
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `a large response arrives intact`() = server.connect().use { client ->
        client.write("GET /big HTTP/1.1\r\nHost: x\r\n\r\n")
        assertContentEquals(ByteArray(BIG) { (it % 251).toByte() }, client.readResponse().body)
    }

    @Test
    fun `Connection close ends the connection after the response`() = server.connect().use { client ->
        client.write("GET / HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n")
        val response = client.readResponse()
        assertEquals("hello", response.text)
        assertEquals("close", response.header("Connection"))
        assertTrue(client.isClosedByPeer())
    }

    @Test
    fun `HTTP 1 0 closes unless it asks to keep alive`() {
        server.connect().use { client ->
            client.write("GET / HTTP/1.0\r\nHost: x\r\n\r\n")
            assertEquals("hello", client.readResponse().text)
            assertTrue(client.isClosedByPeer())
        }

        server.connect().use { client ->
            client.write("GET / HTTP/1.0\r\nHost: x\r\nConnection: keep-alive\r\n\r\n")
            assertEquals("keep-alive", client.readResponse().header("Connection"))
            client.write("GET / HTTP/1.0\r\nHost: x\r\nConnection: keep-alive\r\n\r\n")
            assertEquals("hello", client.readResponse().text)
        }
    }

    @Test
    fun `a malformed request is refused with 400 and the connection closes`() = server.connect().use { client ->
        client.write("GET / HTTP/1.1\r\nHost: x\r\nno colon here\r\n\r\n")
        val response = client.readResponse()
        assertEquals(400, response.status)
        assertTrue(client.isClosedByPeer())
    }

    @Test
    fun `a request without Host is refused`() = server.connect().use { client ->
        client.write("GET / HTTP/1.1\r\n\r\n")
        assertEquals(400, client.readResponse().status)
    }

    @Test
    fun `Expect 100-continue gets its interim response before the body is sent`() = server.connect().use { client ->
        client.write("POST /echo HTTP/1.1\r\nHost: x\r\nContent-Length: 4\r\nExpect: 100-continue\r\n\r\n")
        assertEquals(100, client.readResponse().status)
        client.write("ping")
        assertEquals("ping", client.readResponse().text)
    }

    @Test
    fun `a HEAD response carries the length but no body`() = server.connect().use { client ->
        client.write("HEAD /only-head HTTP/1.1\r\nHost: x\r\n\r\n")
        val response = client.readResponse(head = true)
        assertEquals(200, response.status)
        assertEquals("8", response.header("Content-Length"))
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `a route that throws is answered with 500`() = server.connect().use { client ->
        client.write("GET /fail HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals(500, client.readResponse().status)
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `a coroutine a route launches that fails is logged and the server goes on`() = server.connect().use { client ->
        client.write("GET /launch HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("launched", client.readResponse().text)
        platform.posix.usleep(50_000u)
        client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("hello", client.readResponse().text)
    }

    @Test
    fun `a delay waits its time on the reactor and the call goes on`() {
        server.connect().use { client ->
            val mark = TimeSource.Monotonic.markNow()
            client.write("GET /delay/40 HTTP/1.1\r\nHost: x\r\n\r\n")
            assertEquals("40", client.readResponse().text)
            assertTrue(mark.elapsedNow().inWholeMilliseconds >= 40)
        }
    }

    @Test
    fun `concurrent delays each wait their own time`() {
        val clients = List(50) { server.connect() }
        try {
            val mark = TimeSource.Monotonic.markNow()
            clients.forEachIndexed { i, client -> client.write("GET /delay/${10 + i * 3} HTTP/1.1\r\nHost: x\r\n\r\n") }
            clients.forEachIndexed { i, client -> assertEquals("${10 + i * 3}", client.readResponse().text) }
            // all on one reactor at once: the longest wait, not the sum of them
            assertTrue(mark.elapsedNow().inWholeMilliseconds < 2_000)
        } finally {
            clients.forEach { it.close() }
        }
    }

    @Test
    fun `a delay cut short by a timeout leaves the call to answer`() {
        server.connect().use { client ->
            client.write("GET /timeout HTTP/1.1\r\nHost: x\r\n\r\n")
            assertEquals("timed out", client.readResponse().text)
            client.write("GET / HTTP/1.1\r\nHost: x\r\n\r\n")
            assertEquals("hello", client.readResponse().text)
        }
    }

    @Test
    fun `the status a route sets is sent with its reason`() = server.connect().use { client ->
        client.write("GET /status HTTP/1.1\r\nHost: x\r\n\r\n")
        val response = client.readResponse()
        assertEquals(201, response.status)
        assertEquals("Created", response.reason)
        assertNull(response.header("Connection"))
    }

    private companion object {
        const val BIG = 3 * 1024 * 1024 + 17
    }
}
