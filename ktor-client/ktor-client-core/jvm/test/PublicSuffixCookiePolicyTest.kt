/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublicSuffixCookiePolicyTest {

    @Test
    fun testNormalizesDomainBeforeCallingRules() = runTest {
        val receivedDomains = mutableListOf<String>()
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomains += domain
            true
        }
        val decomposedPunycode = requireNotNull(Punycode.encode("a\u030Alesund"))
        val fullwidthPunycode = requireNotNull(Punycode.encode("\uFF49\uFF4F"))
        val cases = listOf(
            ".A\u030Alesund.NO" to "xn--lesund-hua.no",
            "$decomposedPunycode.no" to "xn--lesund-hua.no",
            "github.\uFF49\uFF4F" to "github.io",
            "GITHUB.\uFF29\uFF2F" to "github.io",
            "github.$fullwidthPunycode" to "github.io"
        )

        for ((domain, _) in cases) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(domain)), domain)
        }
        assertEquals(cases.map { it.second }, receivedDomains)
    }

    @Test
    fun testRejectsSoftHyphenSpellingOfPublicSuffix() = runTest {
        val storage = FilteredCookiesStorage(PublicSuffixCookiePolicy { it == "uk" || it == "co.uk" })
        val cookie = Cookie("session", "injected", domain = "co\u00AD.uk", path = "/")

        storage.addCookie(Url("https://evil.co\u00AD.uk/"), cookie)

        assertTrue(storage.get(Url("https://victim.co\u00AD.uk/")).isEmpty())
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

        // U+2488 normalizes to "1.", so neither Punycode label may hide a separator
        val separatorPunycode = listOf("a\u3002b", "\u2488").map { requireNotNull(Punycode.encode(it)) }
        val domains = listOf("xn--invalid-", ".", "exa\u200Bmple.com") + separatorPunycode.map { "$it.com" }

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
