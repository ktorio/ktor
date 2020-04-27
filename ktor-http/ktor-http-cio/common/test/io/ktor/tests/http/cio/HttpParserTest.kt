/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.test.*

class HttpParserTest {
    private var failure: Throwable? = null

    @Test
    fun parseHeadersSmokeTest(): Unit = test {
        val encodedHeaders = """
            name: value
            name2:${HTAB}p1${HTAB}p2 p3${HTAB}
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
    fun parseHeadersFoldingShouldBeProhibited(): Unit = test {
        val encodedHeaders = "A:\r\n folding\r\n\r\n"
        val channel = ByteReadChannel(encodedHeaders)

        assertFailsWith<ParserException> {
            parseHeaders(channel).release()
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
