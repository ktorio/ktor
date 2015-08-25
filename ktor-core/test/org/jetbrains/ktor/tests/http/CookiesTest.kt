package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.cookies.*
import org.jetbrains.ktor.testing.*
import java.text.*
import java.time.*
import java.time.format.*
import kotlin.test.*
import org.junit.Test as test

class CookiesTest {
    test fun `simple cookies`() {
        withRawCookies("SESSION=123; HOST=abc") {
            assertEquals("123", cookies["SESSION"])
            assertEquals("abc", cookies["HOST"])
        }
    }

    test fun `old fashioned obsolete cookies syntax`() {
        withRawCookies("\$Version=1; SESSION=456; \$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    test fun `missing space`() {
        withRawCookies("SESSION=456;\$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    test fun `comma separator instead of semicolon`() {
        withRawCookies("SESSION=000, HOST=zzz") {
            assertEquals("000", cookies["SESSION"])
            assertEquals("zzz", cookies["HOST"])
        }
    }

    test fun `decode encoded cookies`() {
        withRawCookies("SESSION=1+2") {
            assertEquals("1 2", cookies["SESSION"])
        }
    }

    test fun `decode dquotes encoded cookies`() {
        withRawCookies("SESSION=\"1 2\"") {
            assertEquals("1 2", cookies.decode(CookieEncoding.DQUOTES)["SESSION"])
        }
    }

    test fun `add cookies simple`() {
        testSetCookies("SESSION=123") {
            setCookie("SESSION", "123")
        }
    }

    test fun `add cookies with max age`() {
        testSetCookies("SESSION=123; Max-Age=7") {
            setCookie("SESSION", "123", maxAge = 7)
        }
    }

    test fun `add cookies with expires`() {
        val date = LocalDate.parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(ZoneId.of("GMT"))
        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            setCookie("SESSION", "123", expires = date)
        }
    }

    test fun `add cookies old Date`() {
        val date = SimpleDateFormat("yyyyMMdd z").parse("20150818 GMT")
        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            setCookie("SESSION", "123", expires = date.toInstant())
        }
    }

    test fun `add cookies with expires at specified time zone`() {
        val date = LocalDate.parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(ZoneId.of("Europe/Moscow"))
        testSetCookies("SESSION=123; Expires=Mon, 17 Aug 2015 21:00:00 GMT") {
            setCookie("SESSION", "123", expires = date)
        }
    }

    test fun `add cookies with flags and extensions`() {
        testSetCookies("SESSION=123; Secure; Flag; Test=1") {
            setCookie("SESSION", "123", secure = true, extensions = linkedMapOf("Flag" to null, "Test" to "1"))
        }
    }

    test fun `add cookies expired`() {
        testSetCookies("SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") {
            setCookieExpired("SESSION")
        }
    }

    test fun `add cookies bad characters`() {
        testSetCookies("AB=1%3A2") {
            setCookie("AB", "1:2")
        }
    }

    test fun `add cookies encoding simple no quotes`() {
        testSetCookies("A=1") {
            setCookie("A", "1", encoding = CookieEncoding.DQUOTES)
        }
    }

    test fun `add cookies encoding simple with quotes`() {
        testSetCookies("A=\"1 2\"") {
            setCookie("A", "1 2", encoding = CookieEncoding.DQUOTES)
        }
    }

    test fun `get or set with existing`() {
        withRawCookies("SESSION=1") {
            with(TestApplicationResponse()) {
                getOrSetCookie(this, "SESSION") {
                    fail("Shouldn't be called")
                    "2"
                }
            }
        }
    }

    test fun `get or set with no existing`() {
        withRawCookies("NO_SESSION=1") {
            with(TestApplicationResponse()) {
                getOrSetCookie(this, "SESSION") {
                    "2"
                }

                assertEquals("SESSION=2", header("Set-Cookie"))
            }
        }
    }

    test fun `get or set with missing header`() {
        with(TestApplicationRequest()) {
            with(TestApplicationResponse()) {
                getOrSetCookie(this, "SESSION") {
                    "2"
                }

                assertEquals("SESSION=2", header("Set-Cookie"))
            }
        }
    }

    private fun testSetCookies(expectedHeaderContent: String, block: ApplicationResponse.() -> Unit) {
        val response = TestApplicationResponse()
        with(response, block)
        assertEquals(expectedHeaderContent, response.header("Set-Cookie"))
    }

    private fun withRawCookies(header: String, block: TestApplicationRequest.() -> Unit) {
        with(TestApplicationRequest()) {
            addHeader("Cookie", header)
            block()
        }
    }
}

class DQuotesEncodingTest {
    test fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    test fun `space inside`() {
        testEncode("\"abc 123\"", "abc 123")
    }

    test fun `equals inside`() {
        testEncode("\"abc=123\"", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.DQUOTES)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.DQUOTES), "Decode failed")
    }

}


class URIEncodingTest {
    test fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    test fun `space inside`() {
        testEncode("abc+123", "abc 123")
    }

    test fun `equals inside`() {
        testEncode("abc%3D123", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.URI_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.URI_ENCODING), "Decode failed")
    }
}

