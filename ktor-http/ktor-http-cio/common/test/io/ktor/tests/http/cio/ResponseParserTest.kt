/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ResponseParserTest {
    @Test
    fun parseStatusCodeShouldBeValid() = test {
        listOf(
            "HTTP/1.1 100 OK\r\n",
            "HTTP/1.1 999 OK\r\n",
        ).forEach {
            val response = parseResponse(ByteReadChannel(it))!!
            assertEquals("OK", response.statusText.toString())
        }
    }

    @Test
    fun parseStatusCodeShouldFailWhenOutOfRange() = test {
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 0 OK\r\n"))!!
        }
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 99 OK\r\n"))!!
        }
        assertFailsWith<ParserException> {
            parseResponse(ByteReadChannel("HTTP/1.1 1000 OK\r\n"))!!
        }
    }

    @Test
    fun parseStatusCodeShouldFailWhenStatusCodeIsNegative() = test {
        assertFailsWith<NumberFormatException> {
            parseResponse(ByteReadChannel("HTTP/1.1 -100 OK\r\n"))!!
        }
    }

    @Test
    fun testInvalidResponse() = test {
        val cases = listOf("A", "H", "a")

        for (case in cases) {
            assertFailsWith<ParserException> {
                parseResponse(ByteReadChannel(case + "\r\n"))
            }
        }
    }
}
