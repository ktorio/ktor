/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublicSuffixCookiePolicyTest {

    @Test
    fun testCanonicalizesDomainBeforeCallingRules() = runTest {
        var receivedDomain: String? = null
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomain = domain
            true
        }

        assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(".A\u030Alesund.NO")))
        assertEquals(canonicalizeDomain("ålesund.no"), receivedDomain)
    }

    @Test
    fun testCanonicalizesPunycodeBeforeCallingRules() = runTest {
        var receivedDomain: String? = null
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomain = domain
            true
        }
        val decomposedPunycode = requireNotNull(Punycode.encode("a\u030Alesund"))

        assertFalse(policy.shouldAccept(Url("https://example.no/"), cookie("$decomposedPunycode.no")))
        assertEquals(canonicalizeDomain("ålesund.no"), receivedDomain)
    }

    @Test
    fun testMapsIdnaSeparatorsBeforeCallingRules() = runTest {
        val receivedDomains = mutableListOf<String>()
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomains += domain
            true
        }

        // U+3002 ideographic, U+FF0E fullwidth, and U+FF61 halfwidth ideographic full stops
        for (domain in listOf("s3.amazonaws。com", "s3．amazonaws｡com")) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(domain)), domain)
        }
        assertEquals(listOf("s3.amazonaws.com", "s3.amazonaws.com"), receivedDomains)
    }

    @Test
    fun testMapsCompatibilityCharactersBeforeCallingRules() = runTest {
        val receivedDomains = mutableListOf<String>()
        val policy = PublicSuffixCookiePolicy { domain ->
            receivedDomains += domain
            true
        }
        val fullwidthPunycode = requireNotNull(Punycode.encode("ｉｏ"))

        // Fullwidth "io", fullwidth "IO", and the Punycode form of fullwidth "io"
        for (domain in listOf("github.ｉｏ", "GITHUB.ＩＯ", "github.$fullwidthPunycode")) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie(domain)), domain)
        }
        assertEquals(listOf("github.io", "github.io", "github.io"), receivedDomains)
    }

    @Test
    fun testRejectsPunycodeLabelsThatDecodeToSeparators() = runTest {
        var calls = 0
        val policy = PublicSuffixCookiePolicy {
            calls++
            false
        }
        // An ideographic full stop inside a label, and U+2488 which normalizes to "1."
        val labels = listOf("a。b", "⒈").map { requireNotNull(Punycode.encode(it)) }

        for (label in labels) {
            assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie("$label.com")), label)
        }
        assertEquals(0, calls)
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
    fun testRejectsMalformedDomainWithoutCallingRules() = runTest {
        var calls = 0
        val policy = PublicSuffixCookiePolicy {
            calls++
            false
        }

        assertFalse(policy.shouldAccept(Url("https://example.com/"), cookie("xn--invalid-")))
        assertFalse(policy.shouldAccept(Url("https://example.com./"), cookie(".")))
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
