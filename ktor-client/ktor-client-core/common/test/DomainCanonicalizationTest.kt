/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import kotlin.test.*

class DomainCanonicalizationTest {

    @Test
    fun testCanonicalizesDomains() {
        val cases = listOf(
            "Example.COM" to "example.com",
            "münchen.de" to "xn--mnchen-3ya.de",
            "XN--MNCHEN-3YA.de" to "xn--mnchen-3ya.de",
            "münchen\u3002de" to "xn--mnchen-3ya.de",
            "münchen\uFF0Ede" to "xn--mnchen-3ya.de",
            "münchen\uFF61de" to "xn--mnchen-3ya.de"
        )

        for ((domain, canonical) in cases) {
            assertEquals(canonical, canonicalizeDomainOrNull(domain), domain)
        }
    }

    @Test
    fun testRejectsInvalidDomains() {
        val softHyphenPunycode = requireNotNull(Punycode.encode("co\u00AD"))
        val domains = listOf(
            "",
            ".",
            "example..com",
            "example.com.",
            "-example.com",
            "my_service",
            "xn--invalid-",
            // Whitespace, control, and invisible characters, including a supplementary variation selector
            "co\u2028.uk",
            "co\u0085.uk",
            "co\u00AD.uk",
            "co\u200B.uk",
            "co\u200D.uk",
            "\uFEFFexample.com",
            "example\uFE0F.com",
            "example\uDB40\uDD01.com",
            "$softHyphenPunycode.uk"
        )

        for (domain in domains) {
            assertNull(canonicalizeDomainOrNull(domain), domain)
        }
    }

    @Test
    fun testCanonicalizeValidLabelsKeepsInvalidLabels() {
        val cases = listOf(
            "My_Svc.münchen.de" to "my_svc.xn--mnchen-3ya.de",
            "Example.com." to "example.com.",
            "a\uD800b.münchen.de" to "a\uD800b.xn--mnchen-3ya.de",
            "co\u00AD.uk" to "co\u00AD.uk"
        )

        for ((value, canonical) in cases) {
            assertEquals(canonical, value.canonicalizeValidLabels(), value)
        }
    }
}
