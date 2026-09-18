/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CookiesTest {

    @Test
    fun testCookiesEscape() = runTest {
        val storage = AcceptAllCookiesStorage()
        val cookie = parseServerSetCookieHeader(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10; HttpOnly"
        )
        storage.addCookie("http://localhost/", cookie)

        val plugin = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        plugin.captureHeaderCookies(builder)
        plugin.sendCookiesWith(builder)

        assertEquals(
            "JSESSIONID=jc1wDGgCjR8s72-xdZYYZsLywZdCsiIT86U7X5h7.front10",
            builder.headers[HttpHeaders.Cookie]
        )
    }

    @Test
    fun testCookiesWithPlus() = runTest {
        val storage = AcceptAllCookiesStorage()
        val cookie = parseServerSetCookieHeader("name=some+value; HttpOnly")
        storage.addCookie("http://localhost/", cookie)

        val plugin = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        plugin.captureHeaderCookies(builder)
        plugin.sendCookiesWith(builder)

        assertEquals("name=some+value", builder.headers[HttpHeaders.Cookie])
    }

    @Test
    fun testRequestCookiesAreNotDroppedWhenEmptyStorage() = runTest {
        val feature = HttpCookies(AcceptAllCookiesStorage(), emptyList())
        val builder = HttpRequestBuilder()

        builder.cookie("test", "value")
        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder)

        assertEquals("test=value", builder.headers[HttpHeaders.Cookie])
    }

    @Test
    fun testCookiesAreRenderedWithSpaceInBetween() = runTest {
        val storage = AcceptAllCookiesStorage()
        storage.addCookie("http://localhost/", Cookie("name1", "value1"))
        storage.addCookie("http://localhost/", Cookie("name2", "value2"))
        val feature = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        feature.sendCookiesWith(builder)

        assertContains(builder.headers[HttpHeaders.Cookie]!!, "; ")
    }

    @Test
    fun testRequestCookiesArePreservedWhenAddingCookiesFromStorage() = runTest {
        val storage = AcceptAllCookiesStorage()
        storage.addCookie("http://localhost/", Cookie("SOMECOOKIE", "somevalue"))
        val feature = HttpCookies(storage, emptyList())
        val builder = HttpRequestBuilder()

        builder.cookie("test", "value")
        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder)

        val renderedCookies = builder.headers[HttpHeaders.Cookie]!!.split("; ")
        assertContains(renderedCookies, "test=value")
        assertContains(renderedCookies, "SOMECOOKIE=somevalue")
    }

    @Test
    fun testNoCookieHeaderWhenEmptyStorageAndNoRequestCookies() = runTest {
        val feature = HttpCookies(AcceptAllCookiesStorage(), emptyList())
        val builder = HttpRequestBuilder()

        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder)

        assertNull(builder.headers[HttpHeaders.Cookie])
    }

    @Test
    fun testCapturedHeaderCookiesStoredAsRawPreserveOriginalHeader() = runTest {
        val feature = HttpCookies(AcceptAllCookiesStorage(), emptyList())
        val builder = HttpRequestBuilder()
        val defaultEncodingCookie = Cookie("default", "&%?#=$")
        val rawEncodingCookie = Cookie("raw", "&%?#=$", encoding = CookieEncoding.RAW)
        val base64EncodingCookie = Cookie("base64", "&%?#=$", encoding = CookieEncoding.BASE64_ENCODING)
        val dquotesEncodingCookie = Cookie("dquotes", "&%?#=$", encoding = CookieEncoding.DQUOTES)
        val cookies = listOf(defaultEncodingCookie, rawEncodingCookie, base64EncodingCookie, dquotesEncodingCookie)
            .joinToString("; ", transform = ::renderCookieHeader)

        builder.header(HttpHeaders.Cookie, cookies)
        feature.captureHeaderCookies(builder)
        feature.sendCookiesWith(builder)

        assertEquals(cookies, builder.headers[HttpHeaders.Cookie])
    }

    @Test
    fun testDoesNotAcceptCookieForUnrelatedDomain() = runTest {
        var victimCookie: String? = null
        val client = HttpClient(MockEngine) {
            install(HttpCookies)
            engine {
                addHandler { request ->
                    when (request.url.host) {
                        "evil.com" -> {
                            val cookieValue = "injected=ATTACKER_VALUE; Domain=victim.com; Path=/"
                            val headers = headersOf(HttpHeaders.SetCookie, cookieValue)
                            respond(content = "set", status = HttpStatusCode.OK, headers = headers)
                        }

                        "victim.com" -> {
                            victimCookie = request.headers[HttpHeaders.Cookie]
                            respondOk()
                        }

                        else -> respondError(HttpStatusCode.NotFound)
                    }
                }
            }
        }

        try {
            client.get("http://evil.com/")
            client.get("http://victim.com/app")

            assertNull(victimCookie)
        } finally {
            client.close()
        }
    }
}
