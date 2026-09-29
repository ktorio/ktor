/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PublicSuffixRulesLoadingTest {

    @Test
    fun testLoadsExactWildcardExceptionAndPrivateRules() = runTest {
        val rules = clientWithList(
            publicSuffixList(
                icann = """
                    com
                    uk
                    co.uk
                    *.ck
                    !www.ck
                """.trimIndent(),
                private = "github.io"
            )
        ).use { it.loadPublicSuffixRules() }

        assertTrue(rules.isPublicSuffix("com"))
        assertTrue(rules.isPublicSuffix("co.uk"))
        assertTrue(rules.isPublicSuffix("github.io"))
        assertTrue(rules.isPublicSuffix("foo.ck"))
        assertFalse(rules.isPublicSuffix("www.ck"))
        assertFalse(rules.isPublicSuffix("example.com"))
        assertTrue(rules.isPublicSuffix("unknown"))
    }

    @Test
    fun testCanonicalizesUnicodeRules() = runTest {
        val rules = clientWithList(
            publicSuffixList(icann = "no\na\u030Alesund.no", private = "github.io")
        ).use { it.loadPublicSuffixRules() }
        val policy = PublicSuffixCookiePolicy(rules)

        assertFalse(policy.shouldAccept(Url("https://example.ålesund.no/"), cookie("ålesund.no")))
    }

    @Test
    fun testPropagatesHttpFailure() = runTest {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respond("failure", HttpStatusCode.ServiceUnavailable) }
            }
        }

        val failure = assertFailsWith<ServerResponseException> { client.loadPublicSuffixRules() }
        assertEquals(HttpStatusCode.ServiceUnavailable, failure.response.status)
        client.close()
    }

    @Test
    fun testPropagatesTransportFailure() = runTest {
        val cause = IllegalStateException("connection failed")
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { throw cause }
            }
        }

        val failure = assertFailsWith<IllegalStateException> { client.loadPublicSuffixRules() }
        assertEquals(cause.message, failure.message)
        client.close()
    }

    @Test
    fun testRejectsEmptyAndMalformedLists() = runTest {
        val invalidLists = listOf(
            "",
            "com",
            publicSuffixList(icann = "com", private = "bad rule")
        )

        for (content in invalidLists) {
            clientWithList(content).use { client ->
                assertFailsWith<IllegalArgumentException>(content) { client.loadPublicSuffixRules() }
            }
        }
    }

    private fun clientWithList(content: String): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { respond(content, HttpStatusCode.OK) }
        }
    }

    private fun publicSuffixList(icann: String, private: String): String = """
        // ===BEGIN ICANN DOMAINS===
        $icann
        // ===END ICANN DOMAINS===
        // ===BEGIN PRIVATE DOMAINS===
        $private
        // ===END PRIVATE DOMAINS===
    """.trimIndent()

    private fun cookie(domain: String): Cookie = Cookie("name", "value", domain = domain, path = "/")
}
