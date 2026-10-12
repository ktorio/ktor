/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublicSuffixCookiePolicyTest {

    @Test
    fun testLowercasesDomainBeforeCallingRules() = runTest {
        val receivedDomains = mutableListOf<String>()
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomains += domain
            true
        }
        val cases = listOf(
            ".GitHub.IO" to "github.io",
            "XN--LESUND-HUA.no" to "xn--lesund-hua.no",
            "co.uk" to "co.uk"
        )

        for ((domain, _) in cases) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(domain)), domain)
        }

        assertEquals(cases.map { it.second }, receivedDomains)
    }

    // OkHttp maps U+1C83 to U+0441, so it connects to xn--p1acf where Ktor would compute xn--p1ag837p
    @Test
    fun testRejectsUnicodeSpellingsOfPublicSuffix() = runTest {
        val cases = listOf(
            "https://evil.\u0440\u0443\u1C83/" to "xn--p1ag837p",
            "https://evil.\u0440\u0443\u1C83/" to "\u0440\u0443\u1C83",
            "https://evil.\u0440\u0443\u1C83/" to "\u0440\u0443\u0441",
            "https://evil.\u0440\u0443\u1C83/" to "xn--p1acf",
            "https://evil.xn--p1acf/" to "xn--p1acf",
            "https://evil.co\u00AD.uk/" to "co\u00AD.uk",
            "https://evil.co\u00AD.uk/" to "co.uk"
        )
        val victims = listOf(
            "https://victim.\u0440\u0443\u1C83/",
            "https://victim.\u0440\u0443\u0441/",
            "https://victim.xn--p1acf/",
            "https://victim.xn--p1ag837p/",
            "https://victim.co\u00AD.uk/",
            "https://victim.co.uk/"
        )

        for ((url, domain) in cases) {
            val storage = FilteredCookiesStorage(PublicSuffixCookiePolicy { it == "xn--p1acf" || it == "co.uk" })
            storage.addCookie(Url(url), Cookie("session", "injected", domain = domain, path = "/"))

            for (victim in victims) {
                assertTrue(storage.get(Url(victim)).isEmpty(), "$url, $domain, $victim")
            }
        }
    }

    @Test
    fun testAcceptsPunycodeDomainBelowPublicSuffix() = runTest {
        val storage = FilteredCookiesStorage(PublicSuffixCookiePolicy { it == "xn--p1acf" })
        val cookie = Cookie("session", "value", domain = "example.xn--p1acf", path = "/")
        storage.addCookie(Url("https://www.example.xn--p1acf/"), cookie)

        assertEquals("value", storage.get(Url("https://example.xn--p1acf/")).singleOrNull()?.value)
    }

    @Test
    fun testMissingBlankAndIpDomainsBypassRules() = runTest {
        var calls = 0
        val policy = PublicSuffixCookiePolicy {
            calls++
            true
        }

        assertTrue(policy.shouldAccept(Url("https://example.com/"), cookie(null)))
        assertTrue(policy.shouldAccept(Url("https://example.com/"), cookie("")))
        assertTrue(policy.shouldAccept(Url("https://127.0.0.1/"), cookie("127.0.0.1")))
        assertEquals(0, calls)
    }

    @Test
    fun testRejectsInvalidDomainWithoutCallingRules() = runTest {
        var calls = 0
        val policy = PublicSuffixCookiePolicy {
            calls++
            false
        }

        val domains = listOf(
            ".",
            "example..com",
            "my_service",
            "\u0440\u0443\u0441",
            "\u0440\u0443\u1C83",
            "github.\uFF49\uFF4F",
            "co\u00AD.uk",
            "exa\u200Bmple.com",
            "example\u3002com",
            "co.u\u212A",
            "example.\u0130o"
        )

        for (domain in domains) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(domain)), domain)
        }
        assertEquals(0, calls)
    }

    @Test
    fun testUsesProvidedRules() = runTest {
        val policy = PublicSuffixCookiePolicy { it == "com" }

        assertFalse(policy.shouldAccept(Url("https://com/"), cookie("com")))
        assertTrue(policy.shouldAccept(Url("https://example.com/"), cookie("example.com")))
    }

    private fun cookie(domain: String?): Cookie = Cookie("name", "value", domain = domain, path = "/")
}
