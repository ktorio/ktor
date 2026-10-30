/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.network.sockets.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlin.test.*

abstract class ContentCommonTestSuite<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    hostFactory: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(hostFactory) {

    @Test
    fun testTextContent() = runTest {
        createAndStartServer {
            handle {
                call.respondText("test")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)

            val fields = HeadersBuilder()
            fields.appendAll(headers)
            fields.remove(HttpHeaders.Date)

            val contentType = fields.getAll(HttpHeaders.ContentType)?.single()
            fields.remove(HttpHeaders.ContentType)
            assertNotNull(contentType)
            val parsedContentType = ContentType.parse(contentType)
            assertEquals(ContentType.Text.Plain.withCharset(Charsets.UTF_8), parsedContentType)

            assertEquals("4", headers[HttpHeaders.ContentLength])
            assertEquals("test", bodyAsText())
        }
    }

    @Test
    fun testBinaryUsingChannel() = runTest {
        createAndStartServer {
            handle {
                call.respondBytesWriter {
                    writeByte(25)
                    writeByte(37)
                    writeByte(42)
                }
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals(ContentType.Application.OctetStream, contentType())
            assertTrue(byteArrayOf(25, 37, 42).contentEquals(readRawBytes()))
        }
    }

    @Test
    fun testRequestContentFormData() = runTest {
        createAndStartServer {
            handle {
                val parameters = runCatching { call.receive<Parameters?>() }.getOrNull()
                if (parameters != null) {
                    call.respond(parameters.formUrlEncode())
                } else {
                    call.respond(HttpStatusCode.UnsupportedMediaType)
                }
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody(TextContent(parametersOf("a", "1").formUrlEncode(), ContentType.Application.FormUrlEncoded))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("a=1", bodyAsText())
        }

        withUrl("/") {
            assertEquals(HttpStatusCode.UnsupportedMediaType.value, status.value)
        }
    }

    @Test
    @Http1Only
    open fun testChunked() = runTest {
        val data = ByteArray(16 * 1024) { it.toByte() }
        val size = data.size.toLong()

        createAndStartServer {
            get("/chunked") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.flushAndClose()
                        }
                    }
                )
            }
            get("/pseudo-chunked") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override val contentLength: Long get() = size

                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeFully(data)
                            channel.flushAndClose()
                        }
                    }
                )
            }
            get("/array") {
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override val contentLength: Long get() = size

                        override fun bytes(): ByteArray = data
                    }
                )
            }
            get("/array-chunked") {
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override fun bytes(): ByteArray = data
                    }
                )
            }
            get("/read-channel") {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    }
                )
            }
            get("/fixed-read-channel") {
                call.respond(
                    object : OutgoingContent.ReadChannelContent() {
                        override val contentLength: Long get() = size

                        override fun readFrom(): ByteReadChannel = ByteReadChannel(data)
                    }
                )
            }
        }

        withUrl("/array") {
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertContentEquals(data, call.response.readRawBytes())
        }

        withUrl("/array-chunked") {
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertContentEquals(data, call.response.readRawBytes())
            assertNull(headers[HttpHeaders.ContentLength])
        }

        withUrl("/chunked") {
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertContentEquals(data, call.response.readRawBytes())
            assertNull(headers[HttpHeaders.ContentLength])
        }

        withUrl("/fixed-read-channel") {
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertContentEquals(data, call.response.readRawBytes())
        }

        withUrl("/pseudo-chunked") {
            assertNotEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(size, headers[HttpHeaders.ContentLength]?.toLong())
            assertContentEquals(data, call.response.readRawBytes())
        }

        withUrl("/read-channel") {
            assertNull(headers[HttpHeaders.ContentLength])
            assertEquals("chunked", headers[HttpHeaders.TransferEncoding])
            assertEquals(1, headers.getAll(HttpHeaders.TransferEncoding)!!.size)
            assertContentEquals(data, call.response.readRawBytes())
        }
    }

    @Test
    open fun funkyChunked() = runTest {
        createAndStartServer {
            handle {
                val content = call.receiveText()
                call.respondText(content)
            }
        }

        withRawSocket(port) {
            output.writeStringUtf8(
                "POST / HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: close\r\n" +
                    "Transfer-Encoding: chunked\r\n\r\n" +
                    "5;ext=\"pwned\r\nlol\"\r\n" +
                    "hello\r\n0\r\n\r\n"
            )
            output.flush()

            assertEquals("HTTP/1.1 200 OK", input.readHttpLine())
            var contentLength = 0
            while (true) {
                val line = input.readHttpLine()
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toInt()
                }
            }

            val content = buildString(contentLength) {
                repeat(contentLength) {
                    append(input.readByte().toInt().toChar())
                }
            }
            assertEquals("hello", content)
        }
    }

    @Test
    fun testSendTextWithContentType() = runTest {
        createAndStartServer {
            handle {
                call.respondText("Hello")
            }
        }

        withUrl("/") {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())

            val contentType = ContentType.parse(headers[HttpHeaders.ContentType]!!)
            val pattern = ContentType.Text.Plain
            assertTrue(contentType.match(pattern))
        }
    }

    @Test
    fun testRequestContentString() = runTest {
        createAndStartServer {
            post("/") {
                call.respond(call.receiveText())
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                setBody("Hello")
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("Hello", bodyAsText())
        }
    }

    @Test
    fun testReceivingBodyWithContentLengthMoreThanMaxInt() = runTest {
        createAndStartServer {
            post("/") {
                call.receiveChannel()
                call.respondText { "OK" }
            }
        }

        withUrl(
            "/",
            {
                method = HttpMethod.Post
                headers.append("Content-Length", (Int.MAX_VALUE.toLong() + 1).toString())
                setBody(ByteArray(1))
            }
        ) {
            assertEquals(200, status.value)
            assertEquals("OK", bodyAsText())
        }
    }

    @Test
    fun testAccessingQueryParameterWithoutValue() = runTest {
        createAndStartServer {
            get("/") {
                call.respondText(call.request.queryParameters["auto"].toString())
            }
        }

        withUrl("/?auto") {
            assertEquals(200, status.value)
            assertEquals("", bodyAsText())
        }
    }
}
