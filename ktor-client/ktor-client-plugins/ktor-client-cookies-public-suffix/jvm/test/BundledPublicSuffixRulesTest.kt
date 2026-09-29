/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.plugins.cookies.*
import io.ktor.client.plugins.cookies.publicsuffix.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlin.test.*

class BundledPublicSuffixRulesTest {

    @Test
    fun testBundledIcannPrivateWildcardAndExceptionRules() {
        assertBundledPublicSuffixVectors(PublicSuffixRules.bundled())
    }

    @Test
    fun testBundledUnicodeAndPunycodeRules() = runBlocking {
        val policy = PublicSuffixCookiePolicy(PublicSuffixRules.bundled())

        assertFalse(policy.shouldAccept(Url("https://example.公司.cn/"), cookie("公司.cn")))
        assertFalse(policy.shouldAccept(Url("https://example.xn--55qx5d.cn/"), cookie("xn--55qx5d.cn")))
    }

    @Test
    fun testBundledRulesRejectCompatibilitySpellings() = runBlocking {
        val policy = PublicSuffixCookiePolicy(PublicSuffixRules.bundled())

        // Fullwidth "io" and an ideographic full stop map to github.io and s3.amazonaws.com
        assertFalse(policy.shouldAccept(Url("https://example.github.io/"), cookie("github.ｉｏ")))
        assertFalse(policy.shouldAccept(Url("https://example.s3.amazonaws.com/"), cookie("s3.amazonaws。com")))
    }

    @Test
    fun testConcurrentFirstUse() = runBlocking {
        val rules = PublicSuffixRules.bundled()

        coroutineScope {
            List(100) {
                async(Dispatchers.Default) { rules.isPublicSuffix("github.io") }
            }.awaitAll().forEach(::assertTrue)
        }
    }

    @Test
    fun testBundlesNotice() {
        val noticePath = PUBLIC_SUFFIX_RESOURCE.substringBeforeLast('/') + "/PUBLIC_SUFFIX_LIST_NOTICE.txt"
        val notice = requireNotNull(javaClass.classLoader.getResourceAsStream(noticePath)).bufferedReader().use {
            it.readText()
        }

        assertContains(notice, "https://publicsuffix.org/list/public_suffix_list.dat")
        assertContains(notice, "Mozilla Public License, v. 2.0")
        assertTrue(Regex("SHA-256: [0-9a-f]{64}").containsMatchIn(notice))
    }

    private fun cookie(domain: String): Cookie = Cookie("name", "value", domain = domain, path = "/")
}
