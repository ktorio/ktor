package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import java.io.*
import java.util.zip.*
import kotlin.test.*

class CompressionTest {
    @Test
    fun testCompressionNotSpecified() {
        withTestApplication {
            application.setupCompression()
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", null, null, "text to be compressed")
        }
    }

    @Test
    fun testCompressionUnknownAcceptedEncodings() {
        withTestApplication {
            application.setupCompression()
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", "a,b,c", null, "text to be compressed")
        }
    }

    @Test
    fun testCompressionDefaultDeflate() {
        withTestApplication {
            application.setupCompression()
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", "deflate", "deflate", "text to be compressed")
        }
    }

    @Test
    fun testCompressionDefaultGzip() {
        withTestApplication {
            application.setupCompression()
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", "gzip,deflate", "gzip", "text to be compressed")
        }
    }

    @Test
    fun testAcceptStartContentEncoding() {
        withTestApplication {
            var defaultEncoding = ""

            application.setupCompression {
                defaultEncoding = this.defaultEncoding
            }

            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", "*", defaultEncoding, "text to be compressed")
        }
    }

    @Test
    fun testUnknownEncodingListedEncoding() {
        withTestApplication {
            application.setupCompression()
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
                }
            }

            handleAndAssert("/", "special,gzip,deflate", "gzip", "text to be compressed")
        }
    }

    @Test
    fun testCustomEncoding() {
        withTestApplication {
            application.setupCompression {
                compressorRegistry["special"] = object: CompressionEncoder {
                    override fun open(stream: OutputStream): OutputStream = stream
                }
            }
            application.routing {
                get("/") {
                    response.sendText("text to be compressed")
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
            application.setupCompression {
                minSize = 10L
            }

            application.routing {
                get("/small") {
                    response.header(HttpHeaders.ContentLength, 4)
                    response.sendText("0123")
                }
                get("/big") {
                    response.header(HttpHeaders.ContentLength, 20)
                    response.sendText("01234567890123456789")
                }
                get("/stream") {
                    response.sendText("stream content")
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
            application.setupCompression {
                compressStream = false
            }

            application.routing {
                get("/stream") {
                    response.sendText("stream content")
                }
            }

            handleAndAssert("/stream", "gzip,deflate", null, "stream content")
        }
    }

    @Test
    fun testCustomCondition() {
        withTestApplication {
            application.setupCompression {
                conditions.add {
                    request.parameters["compress"] == "true"
                }
            }

            application.routing {
                get("/") {
                    response.sendText("content")
                }
            }

            handleAndAssert("/", "gzip,deflate", null, "content")
            handleAndAssert("/?compress=true", "gzip,deflate", "gzip", "content")
        }
    }

    private fun TestApplicationHost.handleAndAssert(url: String, acceptHeader: String?, expectedEncoding: String?, expectedContent: String) {
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
    }

    private fun TestApplicationResponse.readDeflate() = InflaterInputStream(byteContent!!.inputStream(), Inflater(true)).reader().readText()
    private fun TestApplicationResponse.readGzip() = GZIPInputStream(byteContent!!.inputStream()).reader().readText()
}