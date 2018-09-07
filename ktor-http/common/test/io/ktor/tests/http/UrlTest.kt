package io.ktor.tests.http

import io.ktor.http.*
import kotlin.random.*
import kotlin.test.*

class UrlTest {
    @Test
    fun testBasicProperties() {
        val urlString = "https://ktor.io/quickstart/?query=string&param=value&param=value2#fragment"
        val url = Url(urlString)
        assertEquals("https", url.protocol.name)
        assertEquals(443, url.port)
        assertEquals(443, url.protocol.defaultPort)
        assertEquals("ktor.io", url.host)
        assertEquals("/quickstart/", url.encodedPath)
        assertEquals(parametersOf("query" to listOf("string"), "param" to listOf("value", "value2")), url.parameters)
        assertEquals("fragment", url.fragment)
        assertEquals(null, url.user)
        assertEquals(null, url.password)
        assertEquals(false, url.trailingQuery)
        assertEquals(urlString, "$url")
    }

    @Test
    fun testOtherPort() {
        val urlString = "http://user:password@ktor.io:8080/"
        val url = Url(urlString)
        assertEquals("http", url.protocol.name)
        assertEquals(8080, url.port)
        assertEquals("user", url.user)
        assertEquals("password", url.password)
        assertEquals(urlString, "$url")
    }

    @Test
    fun testAuthInfoWithoutPassword() {
        val urlString = "http://user@ktor.io:8080/"
        val url = Url(urlString)
        assertEquals("http", url.protocol.name)
        assertEquals(8080, url.port)
        assertEquals("user", url.user)
        assertEquals(null, url.password)
        assertEquals(urlString, "$url")
    }

    @Test
    fun testIpV6() {
        val urlString = "https://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]:8080/hello"
        val url = Url(urlString)

        assertEquals("https", url.protocol.name)
        assertEquals(8080, url.port)
        assertEquals("[2001:0db8:85a3:0000:0000:8a2e:0370:7334]", url.host)
        assertEquals("/hello", url.encodedPath)
        assertEquals(null, url.user)
        assertEquals(null, url.password)
        assertEquals(false, url.trailingQuery)
        assertEquals(urlString, url.toString())
    }

    @Test
    fun testIpV4() {
        val urlString = "//127.0.0.1/hello"
        val url = Url(urlString)

        assertEquals("http", url.protocol.name)
        assertEquals("127.0.0.1", url.host)
        assertEquals("/hello", url.encodedPath)
        assertEquals(null, url.user)
        assertEquals(null, url.password)
        assertEquals(false, url.trailingQuery)
        assertEquals("http:$urlString", url.toString())
    }

    /**
     * https://tools.ietf.org/html/rfc1738#section-5
     * hsegment = *[ uchar | ";" | ":" | "@" | "&" | "=" ]
     */
    @Test
    fun testPath() {
        val cases = listOf(';', ':', '@', '&', '=')
        cases.forEach { case ->
            val url = Url("http://localhost/foo${case}bar")
            assertEquals("http", url.protocol.name)
            assertNull(url.user)
            assertNull(url.password)
            assertEquals("/foo${case}bar", url.encodedPath)

            assertEquals("http://localhost/foo${case}bar", url.toString())
        }
    }

    @Test
    fun testEncoding() {
        val urlBuilder = {
            URLBuilder("http://httpbin.org/response-headers").apply {
                parameters.append("message", "foo%bar")
            }
        }

        val url = urlBuilder().build()

        assertEquals("http://httpbin.org/response-headers?message=foo%25bar", urlBuilder().buildString())
        assertEquals("http://httpbin.org/response-headers?message=foo%25bar", url.toString())
        assertEquals("/response-headers", url.encodedPath)
        assertEquals("/response-headers?message=foo%25bar", url.fullPath)
    }

    @Test
    fun testHugeUrl() {
        val parts = (0 until 100).map { "this is a very long string" }
        val url = "http://127.0.0.1:8080?a=${parts.joinToString("\n") { it }}"

        Url(url)
    }

    @Test
    fun testMissingSlash() {
        val urlString = "https://www.test.com?test=ok&authtoken=testToken"
        val url = Url(urlString)

        with(url) {
            assertEquals(URLProtocol.HTTPS, protocol)
            assertEquals("www.test.com", host)
            assertEquals("", encodedPath)
            assertEquals("https://www.test.com/?test=ok&authtoken=testToken", url.toString())
        }
    }

    @Test
    fun testPortRange() {
        fun testPort(n: Int) {
            assertEquals(n, Url(URLProtocol.HTTP, "localhost", n, "/", parametersOf(), "", null, null, false).specifiedPort)
        }

        // smallest port value
        testPort(0)
        // largest port value 2^16
        testPort(65535)

        // Test a random port in the range
        testPort(Random.nextInt(65535).coerceAtLeast(0))

        assertFails { testPort(-2) }
    }
}
