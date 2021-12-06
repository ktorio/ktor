/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

@Suppress("DEPRECATION")
class CookiesTest {
    @Test
    fun `simple cookies`() {
        withRawCookies("SESSION=123; HOST=abc") {
            assertEquals("123", cookies["SESSION"])
            assertEquals("abc", cookies["HOST"])
        }
    }

    @Test
    fun `old fashioned obsolete cookies syntax`() {
        withRawCookies("\$Version=1; SESSION=456; \$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @Test
    fun `missing space`() {
        withRawCookies("SESSION=456;\$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @Test
    fun `decode encoded cookies`() {
        withRawCookies("SESSION=1+2") {
            assertEquals("1 2", cookies["SESSION"])
        }
    }

    @Test
    fun `decode dquotes encoded cookies`() {
        withRawCookies("SESSION=\"1 2\"") {
            assertEquals("1 2", cookies["SESSION", CookieEncoding.DQUOTES])
        }
    }

    @Test
    fun `add cookies simple`() {
        testSetCookies("SESSION=123") {
            cookies.append("SESSION", "123")
        }
    }

    @Test
    fun `add cookies with max age`() {
        testSetCookies("SESSION=123; Max-Age=7") {
            cookies.append("SESSION", "123", maxAge = 7L)
        }
    }

    @Test
    fun `add cookies with flags and extensions`() {
        testSetCookies("SESSION=123; Secure; Flag; Test=1") {
            cookies.append("SESSION", "123", secure = true, extensions = linkedMapOf("Flag" to null, "Test" to "1"))
        }
    }

    @Test
    fun `add cookies expired`() {
        testSetCookies("SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") {
            cookies.appendExpired("SESSION")
        }
    }

    @Test
    fun `add cookies bad characters`() {
        testSetCookies("AB=1%3A2") {
            cookies.append("AB", "1:2")
        }
    }

    @Test
    fun `add cookies encoding simple no quotes`() {
        testSetCookies("A=1") {
            cookies.append("A", "1", encoding = CookieEncoding.DQUOTES)
        }
    }

    @Test
    fun `add cookies encoding simple with quotes`() {
        testSetCookies("A=\"1 2\"") {
            cookies.append("A", "1 2", encoding = CookieEncoding.DQUOTES)
        }
    }

    @Test
    fun `add cookie and get it`() {
        withTestApplicationResponse {
            cookies.append("key", "value")

            assertEquals("value", cookies["key"]?.value)
            assertNull(cookies["other"])
        }
    }

    @Test
    fun `add multiple cookies`() {
        withTestApplicationResponse {
            cookies.append("a", "1")
            cookies.append("b", "2")

            assertEquals(listOf("a=1", "b=2"), headers.values("Set-Cookie").map { it.cutSetCookieHeader() })
        }
    }

    @Test
    fun testSecureCookieHttp() {
        withTestApplication {
            application.routing {
                get("/*") {
                    call.response.cookies.append("S", "secret", secure = true)
                    call.respond("ok")
                }
            }

            assertFails {
                handleRequest(HttpMethod.Get, "/1")
            }
        }
    }

    @Test
    fun testSecureCookieHttps() {
        withTestApplication {
            application.routing {
                get("/*") {
                    call.response.cookies.append("S", "secret", secure = true)
                    call.respond("ok")
                }
            }

            handleRequest(HttpMethod.Get, "/2") {
                protocol = "https"
            }
        }
    }

    private fun testSetCookies(expectedHeaderContent: String, block: ApplicationResponse.() -> Unit) {
        withTestApplicationResponse {
            block()
            assertEquals(expectedHeaderContent, headers["Set-Cookie"]?.cutSetCookieHeader())
        }
    }

    private fun withTestApplicationResponse(block: TestApplicationResponse.() -> Unit) {
        withTestApplication {
            createCall { protocol = "https" }.response.apply(block)
        }
    }

    private fun withRawCookies(header: String, block: TestApplicationRequest.() -> Unit) {
        withTestApplication {
            createCall { protocol = "https" }.request.apply {
                addHeader("Cookie", header)
                block()
            }
        }
    }

    private fun String.cutSetCookieHeader() = substringBeforeLast("\$x-enc").trimEnd().removeSuffix(";")
}

class DQuotesEncodingTest {
    @Test
    fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    @Test
    fun `space inside`() {
        testEncode("\"abc 123\"", "abc 123")
    }

    @Test
    fun `equals inside`() {
        testEncode("abc=123", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.DQUOTES)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.DQUOTES), "Decode failed")
    }
}

class Base64EncodingTest {
    @Test
    fun `no bad characters`() {
        testEncode("YWJj", "abc")
    }

    @Test
    fun `space inside`() {
        testEncode("YWJjIDEyMw==", "abc 123")
    }

    @Test
    fun `equals inside`() {
        testEncode("YWJjPTEyMw==", "abc=123")
    }

    private fun testEncode(expected: String, value: String): String {
        val encoded = encodeCookieValue(value, CookieEncoding.BASE64_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.BASE64_ENCODING), "Decode failed")
        return encoded
    }
}

class URIEncodingTest {
    @Test
    fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    @Test
    fun `space inside`() {
        testEncode("abc+123", "abc 123")
    }

    @Test
    fun `equals inside`() {
        testEncode("abc%3D123", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.URI_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.URI_ENCODING), "Decode failed")
    }
}

class RawCookieTest {
    @Test
    fun testRawEncodingWithEquals() {
        val cookieValue = "my.value.key=my.value.value+my.value.id=5"
        val encoded = encodeCookieValue(cookieValue, CookieEncoding.RAW)
        assertEquals(cookieValue, encoded)
    }
}

class ParserServerSetCookieTest {
    @Test
    fun testSimpleParse() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testSimpleParseCustomEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=RAW"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.RAW, parsed.encoding)
    }

    @Test
    fun testSimpleParseMissingEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testSimpleParseVersionAtStart() {
        val header = "\$Version=1; key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testParseWithQuotes() {
        val header = "key=\"aaa; bbb = ccc\"; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa; bbb = ccc", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @Test
    fun testParseExpires() {
        val header = "SESSION=cart%3D%2523cl%26userId%3D%2523sid1; " +
            "Expires=Sat, 16 Jan 2016 13:43:28 GMT; HttpOnly; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        val expires = parsed.expires
        assertNotNull(expires)
        assertEquals(2016, expires.year)
        assertEquals(io.ktor.util.date.Month.JANUARY, expires.month)
        assertEquals(16, expires.dayOfMonth)
    }

    @Test
    fun testParseBase64() {
        val header = "SESSION=MTIzCg==; \$x-enc=BASE64_ENCODING"
        val parsed = parseServerSetCookieHeader(header)
        assertEquals("123\n", parsed.value)
    }

    @Test
    fun testMaxAge() {
        val header = "key=aaa; max-age=999"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(999, parsed.maxAge)
    }

    @Test
    fun testMaxAgeNegative() {
        val header = "key=aaa; max-age=-1"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(0, parsed.maxAge)
    }

    @Test
    fun testMaxAgeTooLong() {
        val header = "key=aaa; max-age=3153600000"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa", parsed.value)
        assertEquals(Int.MAX_VALUE, parsed.maxAge)
    }

    @Test
    fun testSlash() {
        val header = "384f8bdb/sessid=GLU787LwmQa9uLqnM7nWHzBm; path=/"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("384f8bdb/sessid", parsed.name)
        assertEquals("GLU787LwmQa9uLqnM7nWHzBm", parsed.value)
        assertEquals("/", parsed.path)
    }
}
