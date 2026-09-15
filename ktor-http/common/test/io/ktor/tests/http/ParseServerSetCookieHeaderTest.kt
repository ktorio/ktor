/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http

import io.ktor.http.*
import io.ktor.util.date.*
import kotlin.coroutines.cancellation.*
import kotlin.test.*

class ParseServerSetCookieHeaderTest {
    private val standardDate = "Wed, 21 Oct 2015 07:28:00 GMT"

    @Test
    fun `default parser preserves supported and invalid date behavior`() {
        val parse: (String) -> Cookie = ::parseServerSetCookieHeader
        for (date in listOf(standardDate, "Wednesday, 21-Oct-2015 07:28:00 GMT")) {
            assertEquals(standardDate.fromCookieToGmtDate(), parse("name=value; Expires=$date").expires)
        }
        for (date in listOf("0", "not-a-date", "")) {
            assertNull(parse("name=value; Expires=$date").expires)
        }
    }

    @Test
    fun `custom parser replaces supported dates and preserves other attributes`() {
        var calls = 0
        val header = "name=a+b; Expires=$standardDate; Max-Age=60; Domain=example.com; Path=/test; " +
            "Secure; HttpOnly; SameSite=Lax"
        val cookie = parseServerSetCookieHeader(header) {
            calls++
            assertEquals(standardDate, it)
            GMTDate(0L)
        }

        assertEquals(parseServerSetCookieHeader(header).copy(expires = GMTDate(0L)), cookie)
        assertEquals(1, calls)
    }

    @Test
    fun `custom null does not fall back to a supported date`() {
        val cookie = parseServerSetCookieHeader("name=value; Expires=$standardDate") { null }
        assertEquals("value", cookie.value)
        assertNull(cookie.expires)
    }

    @Test
    fun `custom failures use the same runCatching behavior as built-in parsing`() {
        for (failure in listOf(IllegalArgumentException("Invalid date"), CancellationException(), AssertionError())) {
            val cookie = parseServerSetCookieHeader("name=value; Expires=$standardDate") { throw failure }
            assertEquals("value", cookie.value)
            assertNull(cookie.expires)
        }
    }

    @Test
    fun `missing expires skips parser but empty expires invokes it`() {
        var calls = 0
        val parser: (String) -> GMTDate? = {
            calls++
            assertEquals("", it)
            null
        }
        assertNull(parseServerSetCookieHeader("name=value", parser).expires)
        assertEquals(0, calls)
        assertNull(parseServerSetCookieHeader("name=value; Expires=", parser).expires)
        assertEquals(1, calls)
    }

    @Test
    fun `custom date value is unquoted and trimmed without decoding or lowercasing`() {
        for (attribute in listOf("eXpIrEs=\"Custom%20Date\"", "Expires= Custom%20Date ")) {
            val cookie = parseServerSetCookieHeader("name=value; $attribute") {
                assertEquals("Custom%20Date", it)
                GMTDate(0L)
            }
            assertEquals(0L, cookie.expires?.timestamp)
        }
    }

    @Test
    fun `message overload preserves separate and combined cookies with date commas`() {
        val message = object : HttpMessage {
            override val headers = headersOf(
                HttpHeaders.SetCookie,
                listOf("first=a; Expires=$standardDate,second=b; Expires=0", "third=c")
            )
        }
        val values = mutableListOf<String>()
        val cookies = message.setCookie {
            values += it
            GMTDate(0L)
        }
        assertEquals(listOf(standardDate, "0"), values)
        assertEquals(listOf("first", "second", "third"), cookies.map { it.name })
        assertEquals(listOf(0L, 0L, null), cookies.map { it.expires?.timestamp })

        val defaultParser: (HttpMessage) -> List<Cookie> = HttpMessage::setCookie
        assertEquals(standardDate.fromCookieToGmtDate(), defaultParser(message).first().expires)
        val empty = object : HttpMessage {
            override val headers = Headers.Empty
        }
        assertEquals(emptyList(), empty.setCookie { error("No date to parse") })
    }
}
