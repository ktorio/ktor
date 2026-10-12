/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import kotlin.test.*

class DomainCanonicalizationTest {

    @Test
    fun testAcceptsCanonicalDomains() {
        val domains = listOf("com", "example.com", "xn--mnchen-3ya.de", "a-1.b2.example", "xn--p1ag837p")

        for (domain in domains) {
            assertTrue(domain.isCanonicalDomain(), domain)
        }
    }

    @Test
    fun testRejectsNonCanonicalDomains() {
        val domains = listOf(
            "",
            ".",
            "example..com",
            "example.com.",
            ".example.com",
            "-example.com",
            "example-.com",
            "Example.com",
            "my_service",
            "ex%61mple.com",
            "münchen.de",
            "co\u00AD.uk",
            "exa\u200Bmple.com",
            "münchen\u3002de",
            "a".repeat(64) + ".com",
            List(64) { "abc" }.joinToString(".")
        )

        for (domain in domains) {
            assertFalse(domain.isCanonicalDomain(), domain)
        }
    }

    @Test
    fun testCanonicalizesRuleDomains() {
        val cases = listOf(
            "Example.COM" to "example.com",
            "münchen.de" to "xn--mnchen-3ya.de",
            "公司.cn" to "xn--55qx5d.cn",
            "XN--MNCHEN-3YA.de" to "xn--mnchen-3ya.de"
        )

        for ((domain, canonical) in cases) {
            assertEquals(canonical, canonicalizeDomainOrNull(domain) { it.lowercase() }, domain)
        }
    }

    @Test
    fun testRejectsInvalidRuleDomains() {
        val domains = listOf("", ".", "example..com", "example.com.", "-example.com", "my_service", "a\uD800b.com")

        for (domain in domains) {
            assertNull(canonicalizeDomainOrNull(domain) { it.lowercase() }, domain)
        }
    }
}
