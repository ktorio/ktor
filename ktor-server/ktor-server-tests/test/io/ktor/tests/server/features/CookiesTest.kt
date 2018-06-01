package io.ktor.tests.server.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import java.text.*
import java.time.*
import java.time.format.*
import java.time.temporal.*
import kotlin.test.*
import org.junit.Test as test

class CookiesTest {
    @test fun `simple cookies`() {
        withRawCookies("SESSION=123; HOST=abc") {
            assertEquals("123", cookies["SESSION"])
            assertEquals("abc", cookies["HOST"])
        }
    }

    @test fun `old fashioned obsolete cookies syntax`() {
        withRawCookies("\$Version=1; SESSION=456; \$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @test fun `missing space`() {
        withRawCookies("SESSION=456;\$Path=/; HOST=def") {
            assertEquals("456", cookies["SESSION"])
            assertEquals("def", cookies["HOST"])
        }
    }

    @test fun `decode encoded cookies`() {
        withRawCookies("SESSION=1+2") {
            assertEquals("1 2", cookies["SESSION"])
        }
    }

    @test fun `decode dquotes encoded cookies`() {
        withRawCookies("SESSION=\"1 2\"") {
            assertEquals("1 2", cookies.get("SESSION", CookieEncoding.DQUOTES))
        }
    }

    @test fun `add cookies simple`() {
        testSetCookies("SESSION=123") {
            cookies.append("SESSION", "123")
        }
    }

    @test fun `add cookies with max age`() {
        testSetCookies("SESSION=123; Max-Age=7") {
            cookies.append("SESSION", "123", maxAge = 7)
        }
    }

    @test fun `add cookies with expires`() {
        val date = LocalDate.parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(ZoneId.of("GMT"))
        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date)
        }
    }

    @test fun `add cookies old Date`() {
        val date = SimpleDateFormat("yyyyMMdd z").parse("20150818 GMT")
        testSetCookies("SESSION=123; Expires=Tue, 18 Aug 2015 00:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date.toInstant())
        }
    }

    @test fun `add cookies with expires at specified time zone`() {
        val zoneId = ZoneId.ofOffset("UTC", ZoneOffset.ofHours(3))
        val date = LocalDate.parse("20150818", DateTimeFormatter.ofPattern("yyyyMMdd")).atStartOfDay(zoneId)
        testSetCookies("SESSION=123; Expires=Mon, 17 Aug 2015 21:00:00 GMT") {
            cookies.append("SESSION", "123", expires = date)
        }
    }

    @test fun `add cookies with flags and extensions`() {
        testSetCookies("SESSION=123; Secure; Flag; Test=1") {
            cookies.append("SESSION", "123", secure = true, extensions = linkedMapOf("Flag" to null, "Test" to "1"))
        }
    }

    @test fun `add cookies expired`() {
        testSetCookies("SESSION=; Expires=Thu, 01 Jan 1970 00:00:00 GMT") {
            cookies.appendExpired("SESSION")
        }
    }

    @test fun `add cookies bad characters`() {
        testSetCookies("AB=1%3A2") {
            cookies.append("AB", "1:2")
        }
    }

    @test fun `add cookies encoding simple no quotes`() {
        testSetCookies("A=1") {
            cookies.append("A", "1", encoding = CookieEncoding.DQUOTES)
        }
    }

    @test fun `add cookies encoding simple with quotes`() {
        testSetCookies("A=\"1 2\"") {
            cookies.append("A", "1 2", encoding = CookieEncoding.DQUOTES)
        }
    }

    @test fun `add cookie and get it`() {
        withTestApplicationResponse {
            cookies.append("key", "value")

            assertEquals("value", cookies["key"]?.value)
            assertNull(cookies["other"])
        }
    }

    @test fun `add multiple cookies`() {
        withTestApplicationResponse {
            cookies.append("a", "1")
            cookies.append("b", "2")

            assertEquals(listOf("a=1", "b=2"), headers.values("Set-Cookie").map { it.cutSetCookieHeader() })
        }
    }

    @test
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

    @test
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
    @test fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    @test fun `space inside`() {
        testEncode("\"abc 123\"", "abc 123")
    }

    @test fun `equals inside`() {
        testEncode("\"abc=123\"", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.DQUOTES)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.DQUOTES), "Decode failed")
    }
}


class URIEncodingTest {
    @test fun `no bad characters`() {
        testEncode("abc", "abc")
    }

    @test fun `space inside`() {
        testEncode("abc+123", "abc 123")
    }

    @test fun `equals inside`() {
        testEncode("abc%3D123", "abc=123")
    }

    private fun testEncode(expected: String, value: String) {
        val encoded = encodeCookieValue(value, CookieEncoding.URI_ENCODING)
        assertEquals(expected, encoded, "Encode failed")
        assertEquals(value, decodeCookieValue(encoded, CookieEncoding.URI_ENCODING), "Decode failed")
    }
}

class ParserServerSetCookieTest {
    @test fun testSimpleParse() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @test fun testSimpleParseCustomEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=RAW"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.RAW, parsed.encoding)
    }

    @test fun testSimpleParseMissingEncoding() {
        val header = "key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @test fun testSimpleParseVersionAtStart() {
        val header = "\$Version=1; key=value; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("value", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @test fun testParseWithQuotes() {
        val header = "key=\"aaa; bbb = ccc\"; max-Age=999; \$extension=1; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        assertEquals("key", parsed.name)
        assertEquals("aaa; bbb = ccc", parsed.value)
        assertEquals(999, parsed.maxAge)
        assertEquals("1", parsed.extensions["\$extension"])
        assertEquals(CookieEncoding.URI_ENCODING, parsed.encoding)
    }

    @test fun testParseExpires() {
        val header = "SESSION=cart%3D%2523cl%26userId%3D%2523sid1; Expires=Sat, 16 Jan 2016 13:43:28 GMT; HttpOnly; \$x-enc=URI_ENCODING"
        val parsed = parseServerSetCookieHeader(header)

        val expires = parsed.expires
        assertNotNull(expires)
//        assertEquals(2016, expires!!.get(ChronoField.YEAR))
//        assertEquals(1, expires.get(ChronoField.MONTH_OF_YEAR))
//        assertEquals(16, expires.get(ChronoField.DAY_OF_MONTH))
    }
}
