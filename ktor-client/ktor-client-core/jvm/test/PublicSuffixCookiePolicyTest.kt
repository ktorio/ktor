/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PublicSuffixCookiePolicyTest {

    @Test
    fun testRejectsExactIcannAndPrivatePublicSuffixes() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertRejected(policy, "https://example.com/", "com")
        assertRejected(policy, "https://example.co.uk/", "co.uk")
        assertRejected(policy, "https://user.github.io/", "github.io")
    }

    @Test
    fun testAcceptsRegistrableParentDomains() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertAccepted(policy, "https://www.example.com/", "example.com")
        assertAccepted(policy, "https://sub.example.co.uk/", "example.co.uk")
    }

    @Test
    fun testHandlesWildcardAndExceptionRules() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertRejected(policy, "https://shop.foo.ck/", "foo.ck")
        assertAccepted(policy, "https://www.ck/", "www.ck")
        assertAccepted(policy, "https://shop.www.ck/", "www.ck")
    }

    @Test
    fun testCanonicalizesLeadingDotsAndCase() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertRejected(policy, "https://example.com/", ".COM")
        assertRejected(policy, "https://user.github.io/", ".GitHub.IO")
    }

    @Test
    fun testCanonicalizesUnicodeAndPunycodeRules() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertRejected(policy, "https://example.公司.cn/", "公司.cn")
        assertRejected(policy, "https://example.xn--55qx5d.cn/", "xn--55qx5d.cn")
    }

    @Test
    fun testRejectsPublicSuffixEqualToResponseHost() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertRejected(policy, "https://com/", "com")
        assertRejected(policy, "https://github.io/", "github.io")
    }

    @Test
    fun testMissingAndBlankDomainsAndIpAddressesBypassPolicy() = runTest {
        val policy = PublicSuffixCookiePolicy

        assertAccepted(policy, "https://example.com/", null)
        assertAccepted(policy, "https://example.com/", "")
        assertAccepted(policy, "https://127.0.0.1/", "127.0.0.1")
    }

    @Test
    fun testRejectsMalformedCookieDomain() = runTest {
        assertFalse(
            PublicSuffixCookiePolicy.shouldAccept(
                Url("https://example.com/"),
                Cookie("name", "value", domain = "xn--invalid-", path = "/")
            )
        )
    }

    @Test
    fun testBundledListMetadata() {
        assertEquals("https://publicsuffix.org/list/public_suffix_list.dat", PUBLIC_SUFFIX_LIST_SOURCE)
        assertTrue(PUBLIC_SUFFIX_LIST_SHA256.matches(Regex("[0-9a-f]{64}")))
        assertTrue(PUBLIC_SUFFIX_EXACT_RULE_CHUNKS.isNotEmpty())
        assertTrue(PUBLIC_SUFFIX_WILDCARD_RULE_CHUNKS.isNotEmpty())
        assertTrue(PUBLIC_SUFFIX_EXCEPTION_RULE_CHUNKS.isNotEmpty())
    }

    private suspend fun assertAccepted(policy: CookieAcceptancePolicy, url: String, domain: String?) {
        assertAccepted(policy, Url(url), domain)
    }

    private suspend fun assertAccepted(policy: CookieAcceptancePolicy, url: Url, domain: String?) {
        val storage = FilteredCookiesStorage(policy)
        storage.addCookie(url, Cookie("name", "value", domain = domain, path = "/"))
        assertEquals("value", storage.get(url).single().value)
    }

    private suspend fun assertRejected(policy: CookieAcceptancePolicy, url: String, domain: String) {
        assertRejected(policy, Url(url), domain)
    }

    private suspend fun assertRejected(policy: CookieAcceptancePolicy, url: Url, domain: String) {
        val storage = FilteredCookiesStorage(policy)
        storage.addCookie(url, Cookie("name", "value", domain = domain, path = "/"))
        assertTrue(storage.get(url).isEmpty())
    }
}
