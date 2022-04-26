/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlin.test.*

class RequestParserTest {

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

    @Test
    fun testParseGetRoot() = test {
        val requestText = "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"
        val ch = ByteReadChannel(requestText.toByteArray())

        val request = parseRequest(ch)
        assertNotNull(request)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/", request.uri.toString())
        assertEquals("HTTP/1.1", request.version.toString())

        assertEquals(2, request.headers.size)
        assertEquals("localhost", request.headers["Host"]?.toString())
        assertEquals("close", request.headers["Connection"]?.toString())
    }

    @Test
    fun testParseGetRootAlternativeSpaces() = test {
        val requestText = "GET  /  HTTP/1.1\nHost:  localhost\nConnection:close\n\n"
        val ch = ByteReadChannel(requestText.toByteArray())

        val request = parseRequest(ch)
        assertNotNull(request)
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/", request.uri.toString())
        assertEquals("HTTP/1.1", request.version.toString())

        assertEquals(2, request.headers.size)
        assertEquals("localhost", request.headers["Host"]?.toString())
        assertEquals("close", request.headers["Connection"]?.toString())
    }
}
