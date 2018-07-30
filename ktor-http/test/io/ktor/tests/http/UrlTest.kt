package io.ktor.tests.http

import io.ktor.http.*
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
        val urlString = "127.0.0.1/hello"
        val url = Url(urlString)

        assertEquals("http", url.protocol.name)
        assertEquals("127.0.0.1", url.host)
        assertEquals("/hello", url.encodedPath)
        assertEquals(null, url.user)
        assertEquals(null, url.password)
        assertEquals(false, url.trailingQuery)
        assertEquals("http://$urlString", url.toString())
    }
}
