/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlin.test.*

class ResponseParserTest {
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
    fun testInvalidResponse(): Unit = test {
        val cases = listOf("A", "H", "a")

        for (case in cases) {
            assertFailsWith<ParserException> {
                parseResponse(ByteReadChannel(case))
            }
        }
    }
}
