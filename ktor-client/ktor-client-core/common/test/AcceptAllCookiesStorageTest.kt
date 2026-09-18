/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.test.dispatcher.*
import io.ktor.util.date.*
import kotlin.test.*

class AcceptAllCookiesStorageTest {

    @Test
    fun testStorageUsesMaxAge() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", maxAge = 1)
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStorageUsesExpires() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", expires = GMTDate(1001))
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStoragePrefersMaxAgeOverExpires() = testSuspend {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", expires = GMTDate(1001), maxAge = 2)
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testLongMaxAge() = testSuspend {
        val storage = AcceptAllCookiesStorage()
        val twoYears = 2 * 365 * 24 * 3600
        val cookie = Cookie("name", "value", maxAge = twoYears)
        storage.addCookie(Url("/"), cookie)

        assertEquals(cookie.value, storage.get(Url("/")).single().value)
    }

    @Test
    fun testAcceptsMatchingCookieDomains() = testSuspend {
        val cases = listOf(
            "https://example.com/" to "example.com",
            "https://www.example.com/" to "example.com",
            "https://www.example.com/" to ".example.com",
            "https://www.example.com/" to "EXAMPLE.COM",
            "https://example.com/" to "",
            "https://127.0.0.1/" to "127.0.0.1"
        )

        cases.forEach { (url, domain) ->
            val storage = AcceptAllCookiesStorage()
            storage.addCookie(Url(url), Cookie("name", "value", domain = domain, path = "/"))

            assertEquals("value", storage.get(Url(url)).single().value)
        }
    }

    @Test
    fun testRejectsCookiesForNonMatchingDomains() = testSuspend {
        val cases = listOf(
            Triple("https://evil.com/", "victim.com", "https://victim.com/"),
            Triple("https://notexample.com/", "example.com", "https://example.com/"),
            Triple("https://127.0.0.1/", "0.0.1", "https://0.0.1/")
        )

        cases.forEach { (requestUrl, domain, cookieUrl) ->
            val storage = AcceptAllCookiesStorage()
            storage.addCookie(Url(requestUrl), Cookie("name", "value", domain = domain, path = "/"))

            assertTrue(storage.get(Url(cookieUrl)).isEmpty())
        }
    }

    @Test
    fun testRejectedCookieDoesNotReplaceExistingCookie() = testSuspend {
        val storage = AcceptAllCookiesStorage()
        val victimUrl = Url("https://victim.com/")
        storage.addCookie(victimUrl, Cookie("session", "LEGITIMATE", domain = "victim.com", path = "/"))

        storage.addCookie(
            Url("https://evil.com/"),
            Cookie("session", "ATTACKER", domain = "victim.com", path = "/")
        )

        assertEquals(listOf("LEGITIMATE"), storage.get(victimUrl).map { it.value })
    }
}
