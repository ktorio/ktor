/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PublicSuffixCookiePolicyTest {

    @Test
    fun testRejectsExactIcannAndPrivatePublicSuffixes() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertRejected(policy, "https://example.com/", "com")
        assertRejected(policy, "https://example.co.uk/", "co.uk")
        assertRejected(policy, "https://user.github.io/", "github.io")
    }

    @Test
    fun testAcceptsRegistrableParentDomains() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertAccepted(policy, "https://www.example.com/", "example.com")
        assertAccepted(policy, "https://sub.example.co.uk/", "example.co.uk")
    }

    @Test
    fun testHandlesWildcardAndExceptionRules() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertRejected(policy, "https://shop.foo.ck/", "foo.ck")
        assertAccepted(policy, "https://www.ck/", "www.ck")
        assertAccepted(policy, "https://shop.www.ck/", "www.ck")
    }

    @Test
    fun testCanonicalizesLeadingDotsAndCase() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertRejected(policy, "https://example.com/", ".COM")
        assertRejected(policy, "https://user.github.io/", ".GitHub.IO")
    }

    @Test
    fun testCanonicalizesUnicodeAndPunycodeRules() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertRejected(policy, "https://example.公司.cn/", "公司.cn")
        assertRejected(policy, "https://example.xn--55qx5d.cn/", "xn--55qx5d.cn")
    }

    @Test
    fun testRejectsPublicSuffixEqualToResponseHost() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertRejected(policy, "https://com/", "com")
        assertRejected(policy, "https://github.io/", "github.io")
    }

    @Test
    fun testMissingAndBlankDomainsAndIpAddressesBypassPolicy() = runTest {
        val policy = loadPolicy(TEST_LIST)

        assertAccepted(policy, "https://example.com/", null)
        assertAccepted(policy, "https://example.com/", "")
        assertAccepted(policy, "https://127.0.0.1/", "127.0.0.1")
    }

    @Test
    fun testLoadsListOnlyOnce() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    requests++
                    respondOk(TEST_LIST)
                }
            }
        }

        try {
            val policy = client.loadPublicSuffixCookiePolicy("https://example.test/list.dat")
            assertRejected(policy, "https://example.com/", "com")
            assertRejected(policy, "https://another.com/", "com")
            assertEquals(1, requests)
        } finally {
            client.close()
        }
    }

    @Test
    fun testUnsuccessfulResponseFailsLoading() = runTest {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respondError(HttpStatusCode.NotFound) } }
        }

        try {
            val cause = assertFailsWith<IllegalStateException> {
                client.loadPublicSuffixCookiePolicy("https://example.test/list.dat")
            }
            assertContains(cause.message.orEmpty(), "404")
        } finally {
            client.close()
        }
    }

    @Test
    fun testEmptyResponseFailsLoading() = runTest {
        assertMalformedList("")
        assertMalformedList("// comments only\n")
    }

    @Test
    fun testMalformedRuleFailsLoading() = runTest {
        assertMalformedList("com\nfoo.*.example\n")
        assertMalformedList("com\n!\n")
        assertMalformedList("com\ninvalid rule\n")
        assertMalformedList("com\nxn--invalid-\n")
    }

    private suspend fun assertAccepted(policy: CookieAcceptancePolicy, url: String, domain: String?) {
        val requestUrl = Url(url)
        val storage = FilteredCookiesStorage(policy)
        storage.addCookie(requestUrl, Cookie("name", "value", domain = domain, path = "/"))
        assertEquals("value", storage.get(requestUrl).single().value)
    }

    private suspend fun assertRejected(policy: CookieAcceptancePolicy, url: String, domain: String) {
        val requestUrl = Url(url)
        val storage = FilteredCookiesStorage(policy)
        storage.addCookie(requestUrl, Cookie("name", "value", domain = domain, path = "/"))
        assertTrue(storage.get(requestUrl).isEmpty())
    }

    private suspend fun loadPolicy(content: String): CookieAcceptancePolicy {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respondOk(content) } }
        }
        return try {
            client.loadPublicSuffixCookiePolicy("https://example.test/list.dat")
        } finally {
            client.close()
        }
    }

    private suspend fun assertMalformedList(content: String) {
        val client = HttpClient(MockEngine) {
            engine { addHandler { respondOk(content) } }
        }
        try {
            assertFailsWith<IllegalArgumentException> {
                client.loadPublicSuffixCookiePolicy("https://example.test/list.dat")
            }
        } finally {
            client.close()
        }
    }

    private companion object {
        val TEST_LIST = """
            // ===BEGIN ICANN DOMAINS===
            com
            uk
            co.uk
            ck
            *.ck
            !www.ck
            cn
            公司.cn
            // ===END ICANN DOMAINS===
            // ===BEGIN PRIVATE DOMAINS===
            github.io
            // ===END PRIVATE DOMAINS===
        """.trimIndent()
    }
}
