package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import org.junit.*
import org.junit.Test
import kotlin.test.*

class HeadersTest {
    private val ch = ByteChannel(true)
    private val builder = CharBufferBuilder()

    @After
    fun tearDown() {
        ch.close()
        builder.release()
    }

    @Test
    fun smokeTest() = runBlocking {
        ch.writeStringUtf8("Host: localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        assertEquals("localhost", hh["host"]?.toString())
        assertEquals("localhost", hh["hOst"]?.toString())
        assertEquals("localhost", hh["HOST"]?.toString())
        assertNull(hh["Host "])

        hh.release()
    }

    @Test
    fun extraSpacesLeading() = runBlocking {
        ch.writeStringUtf8(" Host:  localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun extraSpacesMiddle() = runBlocking {
        ch.writeStringUtf8("Host:  localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun extraSpacesMiddleBeforeColon() = runBlocking {
        ch.writeStringUtf8("Host : localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun extraSpacesMiddleBeforeColonNoAfter() = runBlocking {
        ch.writeStringUtf8("Host :localhost\r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun extraSpacesTrailing() = runBlocking {
        ch.writeStringUtf8("Host:  localhost \r\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost ", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsFirst() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\r\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsSecond() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\n\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun alternativeLineSeparatorsBoth() = runBlocking {
        ch.writeStringUtf8("Host: localhost\n\n")
        val hh = parseHeaders(ch, builder)!!

        assertEquals("localhost", hh["Host"]?.toString())
        hh.release()
    }

    @Test
    fun testExpectHttpBodyGet() = runBlocking {
        ch.writeStringUtf8("GET / H\nConnection: close\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyGetAndContentLength() = runBlocking {
        ch.writeStringUtf8("GET / H\nContent-Length: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyGetAndContentLengthNonZero() = runBlocking {
        ch.writeStringUtf8("GET / H\nContent-Length: 10\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentLengthZero() = runBlocking {
        ch.writeStringUtf8("POST / H\nContent-Length: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentLengthNonZero() = runBlocking {
        ch.writeStringUtf8("POST / H\nContent-Length: 10\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentChunked() = runBlocking {
        ch.writeStringUtf8("POST / H\nTransfer-Encoding: chunked\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostContentType() = runBlocking {
        ch.writeStringUtf8("POST / H\nContent-Type: application/json\n\n")
        val request = parseRequest(ch)!!

        try {
            assertTrue { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }

    @Test
    fun testExpectHttpBodyPostOnly() = runBlocking {
        ch.writeStringUtf8("POST / H\nX: 0\n\n")
        val request = parseRequest(ch)!!

        try {
            assertFalse { expectHttpBody(request) }
        } finally {
            request.release()
        }
    }
}