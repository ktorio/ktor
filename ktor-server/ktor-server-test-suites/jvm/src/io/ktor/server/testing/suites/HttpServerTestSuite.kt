/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import java.net.*
import java.nio.*
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.test.*
import kotlin.text.toByteArray

public abstract class HttpServerTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    public fun testRedirect() {
        createAndStartServer {
            handle {
                call.respondRedirect("http://localhost:${call.request.port()}/page", true)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)
        }
    }

    @Test
    public fun testRedirectFromInterceptor() {
        createAndStartServer {
            application.intercept(ApplicationCallPipeline.Features) {
                call.respondRedirect("/2", true)
            }
        }

        withUrl("/1/") {
            assertEquals(HttpStatusCode.MovedPermanently.value, status.value)

            assertEquals("/2", headers[HttpHeaders.Location])
        }
    }

    @Test
    public fun testHeader() {
        createAndStartServer {
            handle {
                call.response.headers.append(HttpHeaders.ETag, "test-etag")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("test-etag", headers[HttpHeaders.ETag])
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    public open fun testHeadRequest() {
        createAndStartServer {
            install(AutoHeadResponse)
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/", { method = HttpMethod.Head }) {
            assertEquals(200, status.value)
            assertNull(headers[HttpHeaders.TransferEncoding])
            assertEquals("5", headers[HttpHeaders.ContentLength])
        }
    }

    @Test
    public fun testCookie() {
        createAndStartServer {
            handle {
                call.response.cookies.append("k1", "v1")
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("k1=v1; \$x-enc=URI_ENCODING", headers[HttpHeaders.SetCookie])
        }
    }

    @Test
    public fun testPathComponentsDecoding() {
        createAndStartServer {
            get("/a%20b") {
                call.respondText("space")
            }
            get("/a+b") {
                call.respondText("plus")
            }
        }

        withUrl("/a%20b") {
            assertEquals(200, status.value)
            assertEquals("space", readText())
        }
        withUrl("/a+b") {
            assertEquals(200, status.value)
            assertEquals("plus", readText())
        }
    }

    @Test
    public fun testFormUrlEncoded() {
        createAndStartServer {
            post("/") {
                call.respondText("${call.parameters["urlp"]},${call.receiveParameters()["formp"]}")
            }
        }

        withUrl("/?urlp=1", {
            method = HttpMethod.Post
            body = ByteArrayContent("formp=2".toByteArray(), ContentType.Application.FormUrlEncoded)
        }) {
            assertEquals(HttpStatusCode.OK.value, status.value)
            assertEquals("1,2", readText())
        }
    }

    @Test
    public fun testRequestTwiceNoKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }

        withUrl("/", {
            header(HttpHeaders.Connection, "close")
        }) {
            assertEquals("Text", readText())
        }

        withUrl("/", {
            header(HttpHeaders.Connection, "close")
        }) {
            assertEquals("Text", readText())
        }
    }

    @Test
    public fun testRequestTwiceWithKeepAlive() {
        createAndStartServer {
            get("/") {
                call.respondText("Text")
            }
        }


        withUrl("/", {
            header(HttpHeaders.Connection, "keep-alive")
        }) {
            assertEquals(200, status.value)
            assertEquals("Text", readText())
        }

        withUrl("/", {
            header(HttpHeaders.Connection, "keep-alive")
        }) {
            assertEquals(200, status.value)
            assertEquals("Text", readText())
        }
    }

    @Test
    public fun testRequestTwiceInOneBufferWithKeepAlive() {
        createAndStartServer {
            get("/") {
                val d = call.request.queryParameters["d"]!!.toLong()
                delay(TimeUnit.SECONDS.toMillis(d))

                call.response.header("D", d.toString())
                call.respondText("Response for $d\n")
            }
        }

        val s = Socket()
        s.tcpNoDelay = true

        val impudent = buildString {
            append("GET /?d=2 HTTP/1.1\r\n")
            append("Host: localhost\r\n")
            append("Connection: keep-alive\r\n")
            append("\r\n")

            append("GET /?d=1 HTTP/1.1\r\n")
            append("Host: localhost\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray()

        s.connect(InetSocketAddress(port))
        s.use { _ ->
            s.getOutputStream().apply {
                write(impudent)
                flush()
            }

            val responses = s.getInputStream().bufferedReader(Charsets.ISO_8859_1).lineSequence()
                .filterNot { line ->
                    line.startsWith("Date") || line.startsWith("Server")
                        || line.startsWith("Content-")
                        || line.toIntOrNull() != null
                        || line.isBlank()
                        || line.startsWith("Connection")
                        || line.startsWith("Keep-Alive")
                }
                .map { it.trim() }
                .joinToString(separator = "\n").replace("200 OK", "200")

            assertEquals(
                """
                HTTP/1.1 200
                D: 2
                Response for 2
                HTTP/1.1 200
                D: 1
                Response for 1
                """.trimIndent().replace("\r\n", "\n"), responses
            )
        }
    }

    @Test
    public fun test404() {
        createAndStartServer {
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }

        withUrl("/aaaa") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
        }
    }

    @Test
    public fun testStatusPages404() {
        createAndStartServer {
            application.install(StatusPages) {
                status(HttpStatusCode.NotFound) {
                    call.respondTextWriter(ContentType.parse("text/html"), HttpStatusCode.NotFound) {
                        write("Error string")
                    }
                }
            }
        }

        withUrl("/non-existent") {
            assertEquals(HttpStatusCode.NotFound.value, status.value)
            assertEquals("Error string", readText())
        }
    }

    @Test
    public fun testRemoteHost() {
        createAndStartServer {
            handle {
                call.respondText {
                    call.request.local.remoteHost
                }
            }
        }

        withUrl("/") {
            readText().also { text ->
                assertNotNull(
                    listOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1").find {
                        it == text
                    }
                )
            }
        }
    }

    @Test
    public fun testRequestParameters() {
        createAndStartServer {
            get("/*") {
                call.respond(call.request.queryParameters.getAll(call.request.path().removePrefix("/")).toString())
            }
        }

        withUrl("/single?single=value") {
            assertEquals("[value]", readText())
        }
        withUrl("/multiple?multiple=value1&multiple=value2") {
            assertEquals("[value1, value2]", readText())
        }
        withUrl("/missing") {
            assertEquals("null", readText())
        }
    }

    @Test
    public fun testClosedConnection() {
        val completed = Job()

        createAndStartServer {
            get("/file") {
                try {
                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            val bb = ByteBuffer.allocate(512)
                            for (i in 1L..1000L) {
                                delay(100)
                                bb.clear()
                                while (bb.hasRemaining()) {
                                    bb.putLong(i)
                                }
                                bb.flip()
                                channel.writeFully(bb)
                                channel.flush()
                            }

                            channel.close()
                        }
                    })
                } finally {
                    completed.cancel()
                }
            }
        }

        socket {
            outputStream.writePacket(RequestResponseBuilder().apply {
                requestLine(HttpMethod.Get, "/file", "HTTP/1.1")
                headerLine("Host", "localhost:$port")
                headerLine("Connection", "keep-alive")
                emptyLine()
            }.build())

            outputStream.flush()

            inputStream.read(ByteArray(100))
        } // send FIN

        runBlocking {
            withTimeout(5000L) {
                completed.join()
            }
        }
    }

    @Test
    public fun testStatusCodeDirect() {
        createAndStartServer {
            get("/") {
                call.response.status(HttpStatusCode.Found)
                call.respond("Hello")
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            assertEquals("Hello", readText())
        }
    }

    @Test
    public fun testStatusCodeViaResponseObject() {
        var completed = false
        createAndStartServer {
            get("/") {
                call.respond(HttpStatusCode.Found)
            }
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.Found.value, status.value)
            completed = true
        }
        assertTrue(completed)
    }

    @Test
    public fun testProxyHeaders() {
        createAndStartServer {
            install(XForwardedHeaderSupport)
            get("/") {
                call.respond(call.url { })
            }
        }

        withUrl("/", {
            header(HttpHeaders.XForwardedHost, "my-host:90")
        }) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host:90/", readText())
        }

        withUrl("/", {
            header(HttpHeaders.XForwardedHost, "my-host")
        }) { port ->
            val expectedProto = if (port == sslPort) "https" else "http"
            assertEquals("$expectedProto://my-host/", readText())
        }

        withUrl("/", {
            header(HttpHeaders.XForwardedHost, "my-host:90")
            header(HttpHeaders.XForwardedProto, "https")
        }) {
            assertEquals("https://my-host:90/", readText())
        }

        withUrl("/", {
            header(HttpHeaders.XForwardedHost, "my-host")
            header(HttpHeaders.XForwardedProto, "https")
        }) {
            assertEquals("https://my-host/", readText())
        }
    }

    @Test
    public fun testRequestParts() {
        createAndStartServer {
            get("/path/1") {
                call.respond(call.request.path())
            }
            get("/document/1") {
                call.respond(call.request.document())
            }
            get("/queryString/1") {
                call.respond(call.request.queryString())
            }
            get("/uri/1") {
                call.respond(call.request.uri)
            }
        }

        withUrl("/path/1?p=v") {
            assertEquals("/path/1", readText())
        }
        withUrl("/path/1?") {
            assertEquals("/path/1", readText())
        }
        withUrl("/path/1") {
            assertEquals("/path/1", readText())
        }

        withUrl("/document/1?p=v") {
            assertEquals("1", readText())
        }
        withUrl("/document/1?") {
            assertEquals("1", readText())
        }
        withUrl("/document/1") {
            assertEquals("1", readText())
        }

        withUrl("/queryString/1?p=v") {
            assertEquals("p=v", readText())
        }
        withUrl("/queryString/1?") {
            assertEquals("", readText())
        }
        withUrl("/queryString/1") {
            assertEquals("", readText())
        }

        withUrl("/uri/1?p=v") {
            assertEquals("/uri/1?p=v", readText())
        }
        withUrl("/uri/1?") {
            assertEquals("/uri/1?", readText())
        }
        withUrl("/uri/1") {
            assertEquals("/uri/1", readText())
        }
    }

    @Test
    public fun testConnectionReset() {
        val completed = Job()

        createAndStartServer {
            get("/file") {
                try {
                    call.respond(object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            val bb = ByteBuffer.allocate(512)
                            for (i in 1L..1000L) {
                                delay(100)
                                bb.clear()
                                while (bb.hasRemaining()) {
                                    bb.putLong(i)
                                }
                                bb.flip()
                                channel.writeFully(bb)
                                channel.flush()
                            }

                            channel.close()
                        }
                    })
                } finally {
                    completed.cancel()
                }
            }
        }

        socket {
            // to ensure immediate RST at close it is very important to set SO_LINGER = 0
            setSoLinger(true, 0)

            outputStream.writePacket(RequestResponseBuilder().apply {
                requestLine(HttpMethod.Get, "/file", "HTTP/1.1")
                headerLine("Host", "localhost:$port")
                headerLine("Connection", "keep-alive")
                emptyLine()
            }.build())

            outputStream.flush()

            inputStream.read(ByteArray(100))
        }  // send FIN + RST

        runBlocking {
            withTimeout(5000L) {
                completed.join()
            }
        }
    }

    @Test
    @Http2Only
    public fun testServerPush() {
        createAndStartServer {
            get("/child") {
                call.respondText("child")
            }

            get("/") {
                call.push("/child")
                call.respondText("test")
            }
        }

        withUrl("/child") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("child", readText())
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("test", readText())
        }
    }

    @Test
    public open fun testUpgrade() {
        val completed = CompletableDeferred<Unit>()

        createAndStartServer {
            get("/up") {
                call.respond(object : OutgoingContent.ProtocolUpgrade() {
                    override val headers: Headers
                        get() = Headers.build {
                            append(HttpHeaders.Upgrade, "up")
                            append(HttpHeaders.Connection, "Upgrade")
                        }

                    override suspend fun upgrade(
                        input: ByteReadChannel,
                        output: ByteWriteChannel,
                        engineContext: CoroutineContext,
                        userContext: CoroutineContext
                    ): Job {
                        return launch(engineContext) {
                            try {
                                val bb = ByteBuffer.allocate(8)
                                input.readFully(bb)
                                bb.flip()
                                output.writeFully(bb)
                                output.close()
                                input.readRemaining().use {
                                    assertEquals(0, it.remaining)
                                }
                                completed.complete(Unit)
                            } catch (t: Throwable) {
                                completed.completeExceptionally(t)
                                throw t
                            }
                        }
                    }
                })
            }
        }

        socket {
            outputStream.apply {
                val p = RequestResponseBuilder().apply {
                    requestLine(HttpMethod.Get, "/up", "HTTP/1.1")
                    headerLine(HttpHeaders.Host, "localhost:$port")
                    headerLine(HttpHeaders.Upgrade, "up")
                    headerLine(HttpHeaders.Connection, "upgrade")
                    emptyLine()
                }.build()
                writePacket(p)
                flush()
            }

            val ch = ByteChannel(true)

            runBlocking {
                launch(coroutineContext) {
                    val s = inputStream
                    val bytes = ByteArray(512)
                    try {
                        while (true) {
                            if (s.available() > 0) {
                                val rc = s.read(bytes)
                                ch.writeFully(bytes, 0, rc)
                            } else {
                                yield()
                                val rc = s.read(bytes)
                                if (rc == -1) break
                                ch.writeFully(bytes, 0, rc)
                            }

                            yield()
                        }
                    } catch (t: Throwable) {
                        ch.close(t)
                    } finally {
                        ch.close()
                    }
                }

                val response = parseResponse(ch)!!

                assertEquals(HttpStatusCode.SwitchingProtocols.value, response.status)
                assertEquals("Upgrade", response.headers[HttpHeaders.Connection]?.toString())
                assertEquals("up", response.headers[HttpHeaders.Upgrade]?.toString())

                (0 until response.headers.size)
                    .map { response.headers.nameAt(it).toString() }
                    .groupBy { it }.forEach { (name, values) ->
                        assertEquals(1, values.size, "Duplicate header $name")
                    }

                outputStream.apply {
                    writePacket {
                        writeLong(0x1122334455667788L)
                    }
                    flush()
                }

                assertEquals(0x1122334455667788L, ch.readLong())

                close()

                completed.await()
            }
        }
    }

    @Test
    public fun testHeadersReturnCorrectly() {
        createAndStartServer {

            get("/") {
                assertEquals("foo", call.request.headers["X-Single-Value"])
                assertEquals("foo,bar", call.request.headers["X-Double-Value"])

                assertNull(call.request.headers["X-Nonexistent-Header"])
                assertNull(call.request.headers.getAll("X-Nonexistent-Header"))

                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        withUrl("/", {
            headers {
                append("X-Single-Value", "foo")
                append("X-Double-Value", "foo")
                append("X-Double-Value", "bar")
            }
        }) {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("OK", readText())
        }
    }
}
