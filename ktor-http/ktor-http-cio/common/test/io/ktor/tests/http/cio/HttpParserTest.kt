/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.test.*

internal const val HTAB: Char = '\u0009'

class HttpParserTest {
    private var failure: Throwable? = null

    @Test
    fun parseHeadersSmokeTest(): Unit = test {
        val encodedHeaders = """
            name: value
            name2:${HTAB}p1${HTAB}p2 p3$HTAB
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)
        val headers = parseHeaders(channel)

        try {
            assertEquals(2, headers.size)
            assertEquals("value", headers["name"].toString())
            assertEquals("p1${HTAB}p2 p3", headers["name2"].toString())
        } finally {
            headers.release()
        }
    }

    @Test
    fun testParseCookieHeader() = test {
        val rawHeaders = "Set-Cookie: ___utmvazauvysSB=kDu\u0001xSkE; path=/; Max-Age=900\r\n\r\n"

        val channel = ByteReadChannel(rawHeaders)
        val headers = parseHeaders(channel)

        try {
            val actual = headers[HttpHeaders.SetCookie].toString()
            assertEquals("___utmvazauvysSB=kDu\u0001xSkE; path=/; Max-Age=900", actual)
        } finally {
            headers.release()
        }
    }

    @Test
    fun parseHeadersNoLeadingSpace(): Unit = test {
        val encodedHeaders = """
            name:value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)
        val headers = parseHeaders(channel)

        try {
            assertEquals(1, headers.size)
            assertEquals("value", headers["name"].toString())
        } finally {
            headers.release()
        }
    }

    @Test
    fun parseHeadersNoLeadingSpaceWithTrailingSpaces(): Unit = test {
        val encodedHeaders = """
            name:value
        """.trimIndent() + "    \r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)
        val headers = parseHeaders(channel)

        try {
            assertEquals(1, headers.size)
            assertEquals("value", headers["name"].toString())
        } finally {
            headers.release()
        }
    }

    @Test
    fun parseHeadersSpaceAfterHeaderNameShouldBeProhibited(): Unit = test {
        val encodedHeaders = """
            name :value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
        }
    }

    @Test
    fun parseHeadersSpacesInHeaderNameShouldBeProhibited(): Unit = test {
        val encodedHeaders = """
            name and more: value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
        }
    }

    @Test
    fun parseHeadersSpacesInHeaderNameShouldBeProhibitedFixed(): Unit = test {
        val encodedHeaders = """
            name-and-more: value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        parseHeaders(channel).release()
    }

    @Test
    fun parseHeadersDelimitersInHeaderNameShouldBeProhibited(): Unit = test {
        val encodedHeaders = """
            name,: value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
        }
    }

    @Test
    fun parseHeadersEmptyHeaderNameShouldBeProhibited(): Unit = test {
        val encodedHeaders = """
            : value
        """.trimIndent() + "\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
        }.let {
            assertTrue("Empty header names are not allowed" in it.message.orEmpty())
        }
    }

    @Test
    fun parseHeadersFoldingShouldBeProhibited(): Unit = test {
        val encodedHeaders = "A:\r\n folding\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
        }
    }

    @Test
    fun parseStatusCodeShouldBeValid(): Unit = test {
        listOf(
            """
            HTTP/1.1 100 OK
            """.trimIndent(),
            """
            HTTP/1.1 999 OK
            """.trimIndent()
        ).forEach {
            val response = parseResponse(ByteReadChannel(it))!!
            assertEquals("OK", response.statusText.toString())
        }
    }

    @Test
    fun parseStatusCodeShouldFailWhenOutOfRange(): Unit = test {
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 0 OK"))!!
        }
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 99 OK"))!!
        }
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 1000 OK"))!!
        }
    }

    @Test
    fun parseStatusCodeShouldFailWhenStatusCodeIsNegative(): Unit = test {
        assertFailsWith<NumberFormatException> {
            parseResponse(ByteReadChannel("HTTP/1.1 -100 OK"))!!
        }
    }

    @Test
    fun testParseVersion() = test {
        val cases = listOf(
            """
        GET / HTTP/1.6
        Host: www.example.com


        """.trimIndent(),
            """
        GET / HTPT/1.1
        Host: www.example.com


        """.trimIndent(),
            """
        GET / _
        Host: www.example.com


        """.trimIndent(),
            """
        GET / HTTP/1.01
        Host: www.example.com


        """.trimIndent()
        )

        for (case in cases) {
            assertFailsWith<ParserException> {
                parseRequest(ByteReadChannel(case))
            }
        }
    }

    @Test
    fun testColonAfterHost() = test {
        val case = """
        GET / HTTP/1.1
        Host: www.example.com:


        """.trimIndent()


        assertFailsWith<ParserException> {
            parseRequest(ByteReadChannel(case))
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun test(block: suspend () -> Unit) {
        var completed = false
        val cont = Continuation<Unit>(EmptyCoroutineContext) {
            completed = true
            failure = it.exceptionOrNull()
        }

        block.startCoroutineCancellable(cont)
        if (!completed) {
            fail("Suspended unexpectedly.")
        }

        failure?.let { throw it }
    }
}
