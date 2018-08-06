package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.io.*
import org.junit.Test
import java.time.*
import java.util.zip.*
import kotlin.test.*

class CompressionTest {
    @Test
    fun testCompressionNotSpecified() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", null, null, "text to be compressed")
        }
    }

    @Test
    fun testCompressionUnknownAcceptedEncodings() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "a,b,c", null, "text to be compressed")
        }
    }

    @Test
    fun testCompressionDefaultDeflate() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "deflate", "deflate", "text to be compressed")
        }
    }

    @Test
    fun testCompressionDefaultGzip() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "gzip,deflate", "gzip", "text to be compressed")
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
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "*", "gzip", "text to be compressed")
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
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "*", "deflate", "text to be compressed")
        }
    }

    @Test
    fun testUnknownEncodingListedEncoding() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "special,gzip,deflate", "gzip", "text to be compressed")
        }
    }

    @Test
    fun testCustomEncoding() {
        withTestApplication {
            application.install(Compression) {
                default()
                encoder("special", object : CompressionEncoder {
                    override fun compress(readChannel: ByteReadChannel) = readChannel
                    override fun compress(writeChannel: ByteWriteChannel) = writeChannel
                })
            }
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            val result = handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "special")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("special", result.response.headers[HttpHeaders.ContentEncoding])
            assertEquals("text to be compressed", result.response.byteContent!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testStatusCode() {
        withTestApplication {
            application.install(Compression)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed", status = HttpStatusCode.NotFound)
                }
            }

            val result = handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "*")
            }
            assertTrue(result.requestHandled)
            assertEquals(HttpStatusCode.NotFound, result.response.status())
            assertEquals("text to be compressed", result.response.byteContent!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testMinSize() {
        withTestApplication {
            application.install(Compression) {
                default()
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
                    call.respondText("OK", ContentType.parse(call.parameters["t"]!!))
                }
            }

            handleAndAssert("/?t=text/plain", "gzip,deflate", "gzip", "OK")
            handleAndAssert("/?t=text/vcard", "gzip,deflate", null, "OK")
            handleAndAssert("/?t=some/other", "gzip,deflate", null, "OK")
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
                    call.respondText("OK")
                }
            }

            handleAndAssert("/?e=1", "gzip", "gzip", "OK")
            handleAndAssert("/?e", "gzip", null, "OK")
            handleAndAssert("/?e", "gzip,deflate", "deflate", "OK")
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
                    call.respondText("OK")
                }
            }

            handleAndAssert("/", "gzip", "gzip", "OK")
            handleAndAssert("/", "deflate", "deflate", "OK")
            handleAndAssert("/", "gzip,deflate", "gzip", "OK")
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
                    call.respondText("OK")
                }
            }

            handleAndAssert("/", "gzip", "gzip", "OK")
            handleAndAssert("/", "deflate", "deflate", "OK")
            handleAndAssert("/", "gzip,deflate", "deflate", "OK")
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
                    call.respondText("OK")
                }
            }

            handleAndAssert("/", "gzip", "gzip", "OK")
            handleAndAssert("/", "deflate", "deflate", "OK")
            handleAndAssert("/", "gzip;q=1,deflate;q=0.1", "gzip", "OK")
            handleAndAssert("/", "gzip;q=0.1,deflate;q=1", "deflate", "OK")
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
                    call.respondText("content")
                }
            }

            handleAndAssert("/", "gzip,deflate", null, "content")
            handleAndAssert("/?compress=true", "gzip,deflate", "gzip", "content")
        }
    }

    @Test
    fun testWithConditionalHeaders() {
        val dateTime = ZonedDateTime.now(GreenwichMeanTime)

        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(CachingHeaders)
            application.install(Compression)

            application.routing {
                get("/") {
                    call.respond(object : OutgoingContent.ReadChannelContent() {
                        init {
                            versions += LastModifiedVersion(dateTime)
                            caching = CachingOptions(
                                    cacheControl = CacheControl.NoCache(CacheControl.Visibility.Public),
                                    expires = dateTime
                            )
                        }

                        override val contentType = ContentType.Text.Plain
                        override val contentLength = 4L
                        override fun readFrom() = ByteReadChannel("test".toByteArray())
                    })
                }
            }

            handleAndAssert("/", "gzip", "gzip", "test").let { call ->
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
                    call.respondWrite {
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
                call.respond(object: OutgoingContent.WriteChannelContent() {
                    override suspend fun writeTo(channel: ByteWriteChannel) {
                        channel.writeStringUtf8("Hello!")
                    }
                })
            }
        }

        handleAndAssert("/", "gzip", "gzip", "Hello!")
    }

    @Test
    fun testCompressionRespondObjectWithIdentity(): Unit = withTestApplication {
        application.install(Compression)

        application.routing {
            get("/") {
                call.respond(object: OutgoingContent.ByteArrayContent() {
                    override val headers: Headers
                        get() = Headers.build {
                            appendAll(super.headers)
                            append(HttpHeaders.ContentEncoding, "identity")
                        }

                    override fun bytes(): ByteArray = "Hello!".toByteArray()
                })
            }
        }

        handleAndAssert("/", "gzip", "identity", "Hello!")
    }

    private fun TestApplicationEngine.handleAndAssert(url: String, acceptHeader: String?, expectedEncoding: String?, expectedContent: String): TestApplicationCall {
        val result = handleRequest(HttpMethod.Get, url) {
            if (acceptHeader != null) {
                addHeader(HttpHeaders.AcceptEncoding, acceptHeader)
            }
        }

        assertEquals(HttpStatusCode.OK, result.response.status())
        if (expectedEncoding != null) {
            assertEquals(expectedEncoding, result.response.headers[HttpHeaders.ContentEncoding])
            when (expectedEncoding) {
                "gzip" -> assertEquals(expectedContent, result.response.readGzip())
                "deflate" -> assertEquals(expectedContent, result.response.readDeflate())
                "identity" -> assertEquals(expectedContent, result.response.readIdentity())
                else -> fail("unknown encoding $expectedEncoding")
            }
        } else {
            assertNull(result.response.headers[HttpHeaders.ContentEncoding], "content shouldn't be compressed")
            assertEquals(expectedContent, result.response.content)
        }

        assertTrue(result.requestHandled)
        return result
    }

    private fun TestApplicationResponse.readIdentity() = byteContent!!.inputStream().reader().readText()
    private fun TestApplicationResponse.readDeflate() = InflaterInputStream(byteContent!!.inputStream(), Inflater(true)).reader().readText()
    private fun TestApplicationResponse.readGzip() = GZIPInputStream(byteContent!!.inputStream()).reader().readText()
}