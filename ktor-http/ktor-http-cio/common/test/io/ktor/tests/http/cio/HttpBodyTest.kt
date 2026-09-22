/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.readBuffer
import kotlin.test.*

class HttpBodyTest {

    @Test
    fun testExpectHttpBodyAcceptsChunked() {
        listOf("chunked", "Chunked", "CHUNKED", " chunked ", "\tchunked").forEach { value ->
            assertTrue(expectHttpBody(HttpMethod.Post, -1, value, null, null), "value: '$value'")
        }
    }

    @Test
    fun testExpectHttpBodyRejectsNonChunkedTransferEncoding() {
        listOf(
            "identity",
            "chunked, identity",
            "identity, chunked",
            "gzip, chunked",
            "chunked, chunked",
            "",
            ",",
            "chunked",
        ).forEach { value ->
            assertFailsWith<ParserException>("value: '$value'") {
                expectHttpBody(HttpMethod.Post, -1, value, null, null)
            }
        }
    }

    @Test
    fun testExpectHttpBodyWithoutTransferEncoding() {
        assertTrue(expectHttpBody(HttpMethod.Post, 5, null, null, null))
        assertFalse(expectHttpBody(HttpMethod.Post, 0, null, null, null))
    }

    @Test
    fun testExpectHttpBodyRequestRejectsDuplicateTransferEncoding() = test {
        val cause = assertFailsWithRequest<ParserException>(
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n"
        ) { expectHttpBody(it) }
        assertEquals("Duplicate Transfer-Encoding header", cause.message)
    }

    @Test
    fun testExpectHttpBodyRequestRejectsChunkedThenIdentity() = test {
        val cause = assertFailsWithRequest<ParserException>(
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: identity\r\nContent-Length: 45\r\n\r\n"
        ) { expectHttpBody(it) }
        assertEquals("Duplicate Transfer-Encoding header", cause.message)
    }

    @Test
    fun testExpectHttpBodyRequestRejectsTransferEncodingWithContentLength() = test {
        val cause = assertFailsWithRequest<ParserException>(
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nContent-Length: 5\r\n\r\n"
        ) { expectHttpBody(it) }
        assertEquals("Transfer-Encoding and Content-Length headers must not be sent together", cause.message)
    }

    @Test
    fun testExpectHttpBodyRequestRejectsDuplicateContentLength() = test {
        val cause = assertFailsWithRequest<ParserException>(
            "POST / HTTP/1.1\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\n"
        ) { expectHttpBody(it) }
        assertEquals("Duplicate Content-Length header", cause.message)
    }

    @Test
    fun testExpectHttpBodyRequestRejectsIdentity() = test {
        assertFailsWithRequest<ParserException>(
            "POST / HTTP/1.1\r\nTransfer-Encoding: identity\r\n\r\n"
        ) { expectHttpBody(it) }
    }

    @Test
    fun testExpectHttpBodyRequestAcceptsChunked() = test {
        withRequest("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n") {
            assertTrue(expectHttpBody(it))
        }
    }

    @Test
    fun testParseHttpBodyHeadersRejectsMalformedFramingBeforeReadingInput() = test {
        listOf(
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: identity\r\n\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\nContent-Length: 5\r\n\r\n",
            "POST / HTTP/1.1\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: identity\r\n\r\n",
            "POST / HTTP/1.1\r\nTransfer-Encoding: chunked, identity\r\n\r\n",
        ).forEach { raw ->
            val input = ByteReadChannel("5\r\nhello\r\n0\r\n\r\n")
            val available = input.availableForRead
            assertFailsWithRequest<ParserException>(raw) { request ->
                parseHttpBody(request.headers, input, ByteChannel())
            }
            assertEquals(available, input.availableForRead, "input must not be consumed for: $raw")
        }
    }

    @Test
    fun testParseHttpBodyHeadersChunked() = test {
        withRequest("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n") { request ->
            val out = ByteChannel()
            parseHttpBody(request.headers, ByteReadChannel("5\r\nhello\r\n0\r\n\r\n"), out)
            out.close()
            assertEquals("hello", out.readBuffer().readText())
        }
    }

    @Test
    fun testParseHttpBodyHeadersContentLength() = test {
        withRequest("POST / HTTP/1.1\r\nContent-Length: 5\r\n\r\n") { request ->
            val out = ByteChannel()
            parseHttpBody(request.headers, ByteReadChannel("hello world"), out)
            out.close()
            assertEquals("hello", out.readBuffer().readText())
        }
    }

    @Test
    fun testParseHttpBodyWithExplicitIdentityFallsBackToContentLength() = test {
        // The low-level overload is shared with the CIO client and intentionally stays lenient.
        val out = ByteChannel()
        parseHttpBody(HttpProtocolVersion.HTTP_1_1, 5, "identity", null, ByteReadChannel("hello world"), out)
        out.close()
        assertEquals("hello", out.readBuffer().readText())
    }

    private suspend fun <T> withRequest(raw: String, block: suspend (Request) -> T): T {
        val request = parseRequest(ByteReadChannel(raw)) ?: fail("Failed to parse request:\n$raw")
        try {
            return block(request)
        } finally {
            request.release()
        }
    }

    private suspend inline fun <reified T : Throwable> assertFailsWithRequest(
        raw: String,
        noinline block: suspend (Request) -> Any?
    ): T = withRequest(raw) { request ->
        assertFailsWith<T>("request:\n$raw") { block(request) }
    }
}
