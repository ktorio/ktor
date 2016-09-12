package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.jetbrains.ktor.util.*
import org.junit.*
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
                    override fun open(delegate: ReadChannel) = delegate
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
    fun testMinSize() {
        withTestApplication {
            application.install(Compression) {
                default()
                minSize(10)
            }

            application.routing {
                get("/small") {
                    call.response.contentLength(4)
                    call.respondText("0123")
                }
                get("/big") {
                    call.response.contentLength(20)
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
                mimeTypeShouldMatch(ContentType.Text.Any)
                excludeMimeTypeMatch(ContentType.Text.VCard)
            }

            application.routing {
                get("/") {
                    call.respondText(ContentType.parse(call.parameters["t"]!!), "OK")
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
        val ldt = LocalDateTime.now()

        withTestApplication {
            application.install(ConditionalHeaders)
            application.install(Compression)

            application.routing {
                get("/") {
                    call.respond(object : Resource, FinalContent.ChannelContent() {
                        override val headers: ValuesMap
                            get() = super.headers

                        override val contentType: ContentType
                            get() = ContentType.Text.Plain

                        override val versions: List<Version>
                            get() = listOf(LastModifiedVersion(ldt))

                        override val expires = ldt

                        override val cacheControl = CacheControl.NoCache(CacheControlVisibility.PUBLIC)

                        override val attributes = Attributes()

                        override val contentLength = 4L

                        override fun channel() = "test".byteInputStream().asAsyncChannel()
                    })
                }
            }

            handleAndAssert("/", "gzip", "gzip", "test").let { call ->
                assertEquals("text/plain", call.response.headers[HttpHeaders.ContentType])
                assertEquals(ldt.toHttpDateString(), call.response.headers[HttpHeaders.Expires])
                assertEquals("no-cache, public", call.response.headers[HttpHeaders.CacheControl])
                assertFalse { HttpHeaders.ContentLength in call.response.headers }
                assertEquals(ldt.toHttpDateString(), call.response.headers[HttpHeaders.LastModified])
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.IfModifiedSince, ldt.toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotModified, call.response.status())
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
                addHeader(HttpHeaders.IfModifiedSince, ldt.toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.NotModified, call.response.status())
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "gzip")
                addHeader(HttpHeaders.IfModifiedSince, ldt.minusHours(1).toHttpDateString())
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("gzip", call.response.headers[HttpHeaders.ContentEncoding])
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.IfModifiedSince, ldt.minusHours(1).toHttpDateString())
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
                    call.respondText(ContentType.Text.Plain, content)
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

    private fun TestApplicationHost.handleAndAssert(url: String, acceptHeader: String?, expectedEncoding: String?, expectedContent: String): TestApplicationCall {
        val result = handleRequest(HttpMethod.Get, url) {
            if (acceptHeader != null) {
                addHeader(HttpHeaders.AcceptEncoding, acceptHeader)
            }
        }

        assertTrue(result.requestHandled)
        assertEquals(HttpStatusCode.OK, result.response.status())
        if (expectedEncoding != null) {
            assertEquals(expectedEncoding, result.response.headers[HttpHeaders.ContentEncoding])
            when (expectedEncoding) {
                "gzip" -> assertEquals(expectedContent, result.response.readGzip())
                "deflate" -> assertEquals(expectedContent, result.response.readDeflate())
                else -> fail("unknown encoding $expectedContent")
            }
        } else {
            assertNull(result.response.headers[HttpHeaders.ContentEncoding], "content shoudln't be compressed")
            assertEquals(expectedContent, result.response.content)
        }

        return result
    }

    private fun TestApplicationResponse.readDeflate() = InflaterInputStream(byteContent!!.inputStream(), Inflater(true)).reader().readText()
    private fun TestApplicationResponse.readGzip() = GZIPInputStream(byteContent!!.inputStream()).reader().readText()
}