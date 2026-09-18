/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import io.ktor.util.date.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AcceptAllCookiesStorageTest {

    @Test
    fun testStorageUsesMaxAge() = runTest {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", maxAge = 1)
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStorageUsesExpires() = runTest {
        var time = 1L
        val storage = AcceptAllCookiesStorage { time }
        val cookie = Cookie("name", "value", expires = GMTDate(1001))
        storage.addCookie(Url("http://localhost/"), cookie)

        assertEquals(cookie.value, storage.get(Url("http://localhost/")).single().value)
        time += 1001
        assertEquals(emptyList(), storage.get(Url("http://localhost/")))
    }

    @Test
    fun testStoragePrefersMaxAgeOverExpires() = runTest {
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
    fun testLongMaxAge() = runTest {
        val storage = AcceptAllCookiesStorage()
        val twoYears = 2 * 365 * 24 * 3600
        val cookie = Cookie("name", "value", maxAge = twoYears)
        storage.addCookie(Url("/"), cookie)

        assertEquals(cookie.value, storage.get(Url("/")).single().value)
    }

    @Test
    fun testAcceptsMatchingCookieDomains() = runTest {
        val cases = listOf(
            "https://example.com/" to "example.com",
            "https://www.example.com/" to "example.com",
            "https://www.example.com/" to ".example.com",
            "https://www.example.com/" to "EXAMPLE.COM",
            "https://example.com/" to "",
            "https://127.0.0.1/" to "127.0.0.1"
        )

        for ((url, domain) in cases) {
            val storage = AcceptAllCookiesStorage()
            val requestUrl = Url(url)
            val cookie = Cookie(name = "name", value = "value", domain = domain, path = "/")
            storage.addCookie(requestUrl, cookie)

            val storedCookie = storage.get(requestUrl).single()
            assertEquals("value", storedCookie.value)
        }
    }

    @Test
    fun testRejectsCookiesForNonMatchingDomains() = runTest {
        val cases = listOf(
            Triple("https://evil.com/", "victim.com", "https://victim.com/"),
            Triple("https://notexample.com/", "example.com", "https://example.com/"),
            Triple("https://127.0.0.1/", "0.0.1", "https://0.0.1/"),
            Triple("https://evil.com./", ".", "https://victim.com./")
        )

        for ((url, domain, cookieUrl) in cases) {
            val storage = AcceptAllCookiesStorage()
            val requestUrl = Url(url)
            val cookie = Cookie(name = "name", value = "value", domain = domain, path = "/")
            storage.addCookie(requestUrl, cookie)

            val storedCookies = storage.get(Url(cookieUrl))
            assertTrue(storedCookies.isEmpty())
        }
    }

    @Test
    fun testRejectedCookieDoesNotReplaceExistingCookie() = runTest {
        val storage = AcceptAllCookiesStorage()
        val victimUrl = Url("https://victim.com/")
        val cookie = Cookie(name = "session", value = "LEGITIMATE", domain = "victim.com", path = "/")
        storage.addCookie(victimUrl, cookie)

        val evilUrl = Url("https://evil.com/")
        val evilCookie = Cookie(name = "session", value = "ATTACKER", domain = "victim.com", path = "/")
        storage.addCookie(evilUrl, evilCookie)

        val storedValues = storage.get(victimUrl).map { it.value }
        assertEquals(listOf("LEGITIMATE"), storedValues)
    }
}
