/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.time.*
import java.util.zip.*
import kotlin.coroutines.*
import kotlin.test.*

class CompressionTest {
    private val textToCompress = "text to be compressed\n".repeat(100)
    private val textToCompressAsBytes = textToCompress.encodeToByteArray()

    @Test
    fun testCompressionNotSpecified() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", null, null, textToCompress)
        }
    }

    @Test
    fun testCompressionUnknownAcceptedEncodings() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "a,b,c", null, textToCompress)
        }
    }

    @Test
    fun testCompressionDefaultDeflate() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "deflate", "deflate", textToCompress)
        }
    }

    @Test
    fun testCompressionDefaultGzip() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "gzip,deflate", "gzip", textToCompress)
        }
    }

    @Test
    fun testAcceptStarContentEncodingGzip() {
        withTestApplication {
            application.install(Compression) {
                gzip()
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "*", "gzip", textToCompress)
        }
    }

    @Test
    fun testShouldNotCompressVideoByDefault() {
        withTestApplication {
            application.install(Compression)

            application.routing {
                get("/") {
                    call.respondText(textToCompress, ContentType.Video.MP4)
                }
            }

            handleAndAssert("/", "*", null, textToCompress)
        }
    }

    @Test
    fun testGzipShouldNotCompressVideoByDefault() {
        withTestApplication {
            application.install(Compression) {
                gzip()
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress, ContentType.Video.MP4)
                }
            }

            handleAndAssert("/", "*", null, textToCompress)
        }
    }

    @Test
    fun testAcceptStarContentEncodingDeflate() {
        withTestApplication {
            application.install(Compression) {
                deflate()
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "*", "deflate", textToCompress)
        }
    }

    @Test
    fun testUnknownEncodingListedEncoding() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "special,gzip,deflate", "gzip", textToCompress)
        }
    }

    @Test
    fun testCustomEncoding() {
        withTestApplication {
            application.install(Compression) {
                default()
                encoder(
                    "special",
                    object : CompressionEncoder {
                        override fun compress(
                            readChannel: ByteReadChannel,
                            coroutineContext: CoroutineContext
                        ) = readChannel

                        override fun compress(
                            writeChannel: ByteWriteChannel,
                            coroutineContext: CoroutineContext
                        ) = writeChannel
                    }
                )
            }
            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            val result = handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "special")
            }
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("special", result.response.headers[HttpHeaders.ContentEncoding])
            assertEquals(textToCompress, result.response.byteContent!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testStatusCode() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(textToCompress, status = HttpStatusCode.Found)
                }
            }

            val result = handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "*")
            }
            assertEquals(HttpStatusCode.Found, result.response.status())
            assertEquals(textToCompress, result.response.byteContent!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testMinSize() {
        withTestApplication {
            application.install(Compression) {
                minimumSize(10)
            }

            application.routing {
                get("/small") {
                    call.respondText("0123")
                }
                get("/big") {
                    call.respondText("01234567890123456789")
                }
                get("/stream") {
                    call.respondText("stream content")
                }
            }

            handleAndAssert("/big", "gzip,deflate", "gzip", "01234567890123456789")
            handleAndAssert("/small", "gzip,deflate", null, "0123")
            handleAndAssert("/stream", "gzip,deflate", "gzip", "stream content")
        }
    }

    @Test
    fun testMinSizeGzip() {
        withTestApplication {
            application.install(Compression) {
                gzip()
                minimumSize(10)
            }

            application.routing {
                get("/small") {
                    call.respondText("0123")
                }
                get("/big") {
                    call.respondText("01234567890123456789")
                }
                get("/stream") {
                    call.respondText("stream content")
                }
            }

            handleAndAssert("/big", "gzip,deflate", "gzip", "01234567890123456789")
            handleAndAssert("/small", "gzip,deflate", null, "0123")
            handleAndAssert("/stream", "gzip,deflate", "gzip", "stream content")
        }
    }

    @Test
    fun testMimeTypes() {
        withTestApplication {
            application.install(Compression) {
                default()
                matchContentType(ContentType.Text.Any)
                excludeContentType(ContentType.Text.VCard)
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress, ContentType.parse(call.parameters["t"]!!))
                }
            }

            handleAndAssert("/?t=text/plain", "gzip,deflate", "gzip", textToCompress)
            handleAndAssert("/?t=text/vcard", "gzip,deflate", null, textToCompress)
            handleAndAssert("/?t=some/other", "gzip,deflate", null, textToCompress)
        }
    }

    @Test
    fun testEncoderLevelCondition() {
        withTestApplication {
            application.install(Compression) {
                gzip {
                    condition {
                        parameters["e"] == "1"
                    }
                }
                deflate()
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/?e=1", "gzip", "gzip", textToCompress)
            handleAndAssert("/?e", "gzip", null, textToCompress)
            handleAndAssert("/?e", "gzip,deflate", "deflate", textToCompress)
        }
    }

    @Test
    fun testEncoderPriority1() {
        withTestApplication {
            application.install(Compression) {
                gzip {
                    priority = 10.0
                }
                deflate {
                    priority = 1.0
                }
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "gzip", "gzip", textToCompress)
            handleAndAssert("/", "deflate", "deflate", textToCompress)
            handleAndAssert("/", "gzip,deflate", "gzip", textToCompress)
        }
    }

    @Test
    fun testEncoderPriority2() {
        withTestApplication {
            application.install(Compression) {
                gzip {
                    priority = 1.0
                }
                deflate {
                    priority = 10.0
                }
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "gzip", "gzip", textToCompress)
            handleAndAssert("/", "deflate", "deflate", textToCompress)
            handleAndAssert("/", "gzip,deflate", "deflate", textToCompress)
        }
    }

    @Test
    fun testEncoderQuality() {
        withTestApplication {
            application.install(Compression) {
                gzip()
                deflate()
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "gzip", "gzip", textToCompress)
            handleAndAssert("/", "deflate", "deflate", textToCompress)
            handleAndAssert("/", "gzip;q=1,deflate;q=0.1", "gzip", textToCompress)
            handleAndAssert("/", "gzip;q=0.1,deflate;q=1", "deflate", textToCompress)
        }
    }

    @Test
    fun testCustomCondition() {
        withTestApplication {
            application.install(Compression) {
                default()
                condition {
                    parameters["compress"] == "true"
                }
            }

            application.routing {
                get("/") {
                    call.respondText(textToCompress)
                }
            }

            handleAndAssert("/", "gzip,deflate", null, textToCompress)
            handleAndAssert("/?compress=true", "gzip,deflate", "gzip", textToCompress)
        }
    }

    @Test
    fun testWithConditionalHeaders() {
        val dateTime = ZonedDateTime.now(ZoneId.of("GMT"))

        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            application.install(Compression)

            application.routing {
                get("/") {
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            init {
                                versions += LastModifiedVersion(dateTime)
                                caching = CachingOptions(
                                    cacheControl = CacheControl.NoCache(CacheControl.Visibility.Public),
                                    expires = dateTime
                                )
                            }

                            override val contentType = ContentType.Text.Plain
                            override val contentLength = textToCompressAsBytes.size.toLong()
                            override fun readFrom() = ByteReadChannel(textToCompressAsBytes)
                        }
                    )
                }
            }

            handleAndAssert("/", "gzip", "gzip", textToCompress).let { call ->
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
                assertEquals(dateTime.toHttpDateString(), call.response.headers[HttpHeaders.Expires])
                assertEquals("no-cache, public", call.response.headers[HttpHeaders.CacheControl])
                assertFalse { HttpHeaders.ContentLength in call.response.headers }
                assertEquals(dateTime.toHttpDateString(), call.response.headers[HttpHeaders.LastModified])
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.IfModifiedSince, dateTime.toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotModified, call.response.status())
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
                addHeader(HttpHeaders.IfModifiedSince, dateTime.toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotModified, call.response.status())
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
                addHeader(HttpHeaders.IfModifiedSince, dateTime.minusHours(1).toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("gzip", call.response.headers[HttpHeaders.ContentEncoding])
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.IfModifiedSince, dateTime.minusHours(1).toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.ContentEncoding])
            }
        }
    }

    @Test
    fun testLargeContent() {
        val content = buildString {
            for (i in 1..16384) {
                append("test$i\n".padStart(10, ' '))
            }
        }

        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText(content)
                }
            }

            handleAndAssert("/", "deflate", "deflate", content)
            handleAndAssert("/", "gzip", "gzip", content)
        }
    }

    @Test
    fun testRespondWrite() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondTextWriter {
                        write("test ")
                        write("me")
                    }
                }
            }

            handleAndAssert("/", "gzip", "gzip", "test me")
        }
    }

    @Test
    fun testCompressionRespondBytes(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/") {
                call.respond(
                    object : OutgoingContent.WriteChannelContent() {
                        override suspend fun writeTo(channel: ByteWriteChannel) {
                            channel.writeStringUtf8("Hello!")
                        }
                    }
                )
            }
        }

        handleAndAssert("/", "gzip", "gzip", "Hello!")
    }

    @Test
    fun testIdentityRequested(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/text") {
                call.respondText(textToCompress)
            }
            get("/bytes") {
                call.respondBytes(textToCompressAsBytes, ContentType.Application.OctetStream)
            }
        }

        handleAndAssert("/text", "identity", "identity", textToCompress)
        handleAndAssert("/bytes", "identity", "identity", textToCompress)
    }

    @Test
    fun testCompressionRespondObjectWithIdentity(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/") {
                call.respond(
                    object : OutgoingContent.ByteArrayContent() {
                        override val headers: Headers
                            get() = Headers.build {
                                appendAll(super.headers)
                                append(HttpHeaders.ContentEncoding, "identity")
                            }

                        override fun bytes(): ByteArray = "Hello!".toByteArray()

                        override val contentLength: Long?
                            get() = 6
                    }
                )
            }
        }

        handleAndAssert("/", "gzip", "identity", "Hello!")
    }

    @Test
    fun testCompressionUpgradeShouldNotBeCompressed(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/") {
                call.respond(
                    object : OutgoingContent.ProtocolUpgrade() {
                        override suspend fun upgrade(
                            input: ByteReadChannel,
                            output: ByteWriteChannel,
                            engineContext: CoroutineContext,
                            userContext: CoroutineContext
                        ): Job {
                            return launch {
                                output.close()
                            }
                        }
                    }
                )
            }
        }

        handleRequest(HttpMethod.Get, "/").let { call ->
            assertEquals(101, call.response.status()?.value)
            assertNull(call.response.headers[HttpHeaders.ContentEncoding])
        }
    }

    @Test
    fun testCompressionContentTypesShouldNotBeCompressed(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/event-stream") {
                call.respondText("events", ContentType.Text.EventStream)
            }
            get("/video") {
                call.respondBytes("video".toByteArray(), ContentType.Video.MPEG)
            }
            get("/multipart") {
                call.respondBytes(
                    ">>>\r\nContent-Disposition: form-data; name=\"title\"\r\n>>>--".toByteArray(),
                    ContentType.MultiPart.FormData.withParameter("boundary", ">>>")
                )
            }
        }

        handleRequest(HttpMethod.Get, "/event-stream").let { call ->
            assertEquals(200, call.response.status()?.value)
            assertNull(call.response.headers[HttpHeaders.ContentEncoding])
            assertEquals("events", call.response.content)
            assertEquals(ContentType.Text.EventStream, call.response.contentType().withoutParameters())
        }
        handleRequest(HttpMethod.Get, "/video").let { call ->
            assertEquals(200, call.response.status()?.value)
            assertNull(call.response.headers[HttpHeaders.ContentEncoding])
            assertEquals("video", call.response.content)
            assertEquals(ContentType.Video.MPEG, call.response.contentType())
        }
        handleRequest(HttpMethod.Get, "/multipart").let { call ->
            assertEquals(200, call.response.status()?.value)
            assertNull(call.response.headers[HttpHeaders.ContentEncoding])
            assertEquals(ContentType.MultiPart.FormData, call.response.contentType().withoutParameters())
        }
    }

    private fun TestApplicationEngine.handleAndAssert(
        url: String,
        acceptHeader: String?,
        expectedEncoding: String?,
        expectedContent: String
    ): TestApplicationCall {
        val result = handleRequest(HttpMethod.Get, url) {
            if (acceptHeader != null) {
                addHeader(HttpHeaders.AcceptEncoding, acceptHeader)
            }
        }

        assertEquals(HttpStatusCode.OK, result.response.status())
        if (expectedEncoding != null) {
            assertEquals(expectedEncoding, result.response.headers[HttpHeaders.ContentEncoding])
            when (expectedEncoding) {
                "gzip" -> {
                    assertEquals(expectedContent, result.response.readGzip())
                    assertNull(result.response.headers[HttpHeaders.ContentLength])
                }
                "deflate" -> {
                    assertEquals(expectedContent, result.response.readDeflate())
                    assertNull(result.response.headers[HttpHeaders.ContentLength])
                }
                "identity" -> {
                    assertEquals(expectedContent, result.response.readIdentity())
                    assertNotNull(result.response.headers[HttpHeaders.ContentLength])
                }
                else -> fail("unknown encoding $expectedEncoding")
            }
        } else {
            assertNull(result.response.headers[HttpHeaders.ContentEncoding], "content shouldn't be compressed")
            assertEquals(expectedContent, result.response.content)
            assertNotNull(result.response.headers[HttpHeaders.ContentLength])
        }

        return result
    }

    private fun TestApplicationResponse.readIdentity() = byteContent!!.inputStream().reader().readText()
    private fun TestApplicationResponse.readDeflate() =
        InflaterInputStream(byteContent!!.inputStream(), Inflater(true)).reader().readText()
    private fun TestApplicationResponse.readGzip() = GZIPInputStream(byteContent!!.inputStream()).reader().readText()
}
