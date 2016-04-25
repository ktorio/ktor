package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
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
            application.install(CompressionSupport)
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
            application.install(CompressionSupport)
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
            application.install(CompressionSupport)
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
            application.install(CompressionSupport)
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "gzip,deflate", "gzip", "text to be compressed")
        }
    }

    @Test
    fun testAcceptStartContentEncoding() {
        withTestApplication {
            var defaultEncoding = ""

            application.install(CompressionSupport) {
                defaultEncoding = this.defaultEncoding
            }

            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            handleAndAssert("/", "*", defaultEncoding, "text to be compressed")
        }
    }

    @Test
    fun testUnknownEncodingListedEncoding() {
        withTestApplication {
            application.install(CompressionSupport)
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
            application.install(CompressionSupport) {
                compressorRegistry["special"] = object : CompressionEncoder {
                    override fun open(delegate: AsyncReadChannel) = delegate
                }
            }
            application.routing {
                get("/") {
                    call.respondText("text to be compressed")
                }
            }

            val result = handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.AcceptEncoding, "special,gzip,deflate")
            }
            assertEquals(ApplicationCallResult.Handled, result.requestResult)
            assertEquals(HttpStatusCode.OK, result.response.status())
            assertEquals("special", result.response.headers[HttpHeaders.ContentEncoding])
            assertEquals("text to be compressed", result.response.byteContent!!.toString(Charsets.UTF_8))
        }
    }

    @Test
    fun testMinSize() {
        withTestApplication {
            application.install(CompressionSupport) {
                minSize = 10L
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
    fun testCompressStreamFalse() {
        withTestApplication {
            application.install(CompressionSupport) {
                compressStream = false
            }

            application.routing {
                get("/stream") {
                    call.respondText("stream content")
                }
            }

            handleAndAssert("/stream", "gzip,deflate", null, "stream content")
        }
    }

    @Test
    fun testCustomCondition() {
        withTestApplication {
            application.install(CompressionSupport) {
                conditions.add {
                    request.parameters["compress"] == "true"
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
            application.install(ConditionalHeadersSupport)
            application.install(CompressionSupport)

            application.routing {
                get("/") {
                    call.respond(object: Resource, ChannelContentProvider {
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

    private fun TestApplicationHost.handleAndAssert(url: String, acceptHeader: String?, expectedEncoding: String?, expectedContent: String): TestApplicationCall {
        val result = handleRequest(HttpMethod.Get, url) {
            if (acceptHeader != null) {
                addHeader(HttpHeaders.AcceptEncoding, acceptHeader)
            }
        }

        assertEquals(ApplicationCallResult.Handled, result.requestResult)
        assertEquals(HttpStatusCode.OK, result.response.status())
        if (expectedEncoding != null) {
            assertEquals(expectedEncoding, result.response.headers[HttpHeaders.ContentEncoding])
            when (expectedEncoding) {
                "gzip" -> assertEquals(expectedContent, result.response.readGzip())
                "deflate" -> assertEquals(expectedContent, result.response.readDeflate())
                else -> fail("unknown encoding $expectedContent")
            }
        } else {
            assertEquals(expectedContent, result.response.content)
        }

        return result
    }

    private fun TestApplicationResponse.readDeflate() = InflaterInputStream(byteContent!!.inputStream(), Inflater(true)).reader().readText()
    private fun TestApplicationResponse.readGzip() = GZIPInputStream(byteContent!!.inputStream()).reader().readText()
}