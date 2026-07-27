/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.jvm.javaio.*
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.test.*

/**
 * Tests for `Accept-Encoding` quality-value negotiation in the [Compression] plugin (RFC 7231 5.3.4):
 * `q=0` excludes a coding, an explicitly named coding overrides a `*` wildcard, and selection is by
 * effective quality and then configured priority.
 */
class CompressionAcceptEncodingTest {
    private val textToCompress = "text to be compressed\n".repeat(100)

    @Test
    fun `q=0 on the only matching coding sends the response uncompressed`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip;q=0", null, textToCompress)
    }

    @Test
    fun `wildcard q=0 excludes every encoder`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "*;q=0", null, textToCompress)
    }

    @Test
    fun `explicit q=0 is excluded while the wildcard enables the rest`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip;q=0,*;q=0.5", "deflate", textToCompress)
    }

    @Test
    fun `explicit coding overrides wildcard q=0`() = testApplication {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 10.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        // deflate has the higher priority but is forbidden via `*;q=0`; only gzip stays eligible.
        handleAndAssert("/", "gzip;q=1,*;q=0", "gzip", textToCompress)
    }

    @Test
    fun `explicit qvalue overrides the wildcard qvalue for the same coding`() = testApplication {
        install(Compression) {
            gzip { priority = 10.0 }
            deflate { priority = 1.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        // gzip's effective quality is its explicit 0.1, not the wildcard 0.9; deflate (0.9 via `*`) wins.
        handleAndAssert("/", "gzip;q=0.1,*;q=0.9", "deflate", textToCompress)
    }

    @Test
    fun `coding match is case-insensitive`() = testApplication {
        install(Compression) {
            gzip { priority = 10.0 }
            deflate { priority = 1.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        // `GZIP;q=0` forbids gzip despite the case difference, so deflate (0.5 via `*`) is used.
        handleAndAssert("/", "GZIP;q=0,*;q=0.5", "deflate", textToCompress)
    }

    @Test
    fun `highest qvalue wins`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip;q=0.2,deflate;q=0.8", "deflate", textToCompress)
    }

    @Test
    fun `equal qvalue falls back to configured priority`() = testApplication {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 2.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip;q=0.5,deflate;q=0.5", "deflate", textToCompress)
    }

    @Test
    fun `equal qvalue tie-break uses priority not match specificity`() = testApplication {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 10.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        // gzip (explicit) and deflate (via `*`) both resolve to 0.5; priority decides, so deflate wins.
        handleAndAssert("/", "gzip;q=0.5,*;q=0.5", "deflate", textToCompress)
    }

    @Test
    fun `wildcard alone selects the highest priority encoder`() = testApplication {
        install(Compression) {
            gzip { priority = 1.0 }
            deflate { priority = 10.0 }
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "*", "deflate", textToCompress)
    }

    @Test
    fun `missing q is treated as 1`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip,deflate;q=0.5", "gzip", textToCompress)
    }

    @Test
    fun `malformed q is treated as 1`() = testApplication {
        install(Compression) {
            gzip()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "gzip;q=abc", "gzip", textToCompress)
    }

    @Test
    fun `coding absent from the header is not eligible`() = testApplication {
        install(Compression) {
            gzip()
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "deflate;q=0.5", "deflate", textToCompress)
    }

    @Test
    fun `no eligible encoder leaves the response uncompressed`() = testApplication {
        install(Compression) {
            gzip()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/", "br", null, textToCompress)
    }

    @Test
    fun `encoder whose condition fails falls through to the next eligible encoder`() = testApplication {
        install(Compression) {
            gzip {
                condition { parameters["e"] == "1" }
            }
            deflate()
        }
        routing { get("/") { call.respondText(textToCompress) } }

        handleAndAssert("/?e=0", "gzip;q=1,deflate;q=0.5", "deflate", textToCompress)
    }

    private suspend fun ApplicationTestBuilder.handleAndAssert(
        url: String,
        acceptHeader: String?,
        expectedEncoding: String?,
        expectedContent: String,
    ) {
        val response = client.get(url) {
            if (acceptHeader != null) {
                header(HttpHeaders.AcceptEncoding, acceptHeader)
            }
        }

        assertEquals(HttpStatusCode.OK, response.status)
        if (expectedEncoding != null) {
            assertEquals(expectedEncoding, response.headers[HttpHeaders.ContentEncoding])
            when (expectedEncoding) {
                "gzip" -> {
                    assertEquals(expectedContent, response.readGzip())
                    assertNull(response.headers[HttpHeaders.ContentLength])
                }

                "deflate" -> {
                    assertEquals(expectedContent, response.readDeflate())
                    assertNull(response.headers[HttpHeaders.ContentLength])
                }

                else -> fail("unexpected encoding $expectedEncoding")
            }
        } else {
            assertNull(response.headers[HttpHeaders.ContentEncoding], "content shouldn't be compressed")
            assertEquals(expectedContent, response.bodyAsText())
        }
    }

    private suspend fun HttpResponse.readGzip() = GZIPInputStream(bodyAsChannel().toInputStream()).reader().readText()

    private suspend fun HttpResponse.readDeflate() =
        InflaterInputStream(bodyAsChannel().toInputStream(), Inflater(true)).reader().readText()
}
