/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.plugins

import io.ktor.http.*
import io.ktor.server.plugins.cors.*
import kotlin.test.*

class CORSTest {

    @Test
    fun originValidation() {
        val plugin = CORS(
            CORS.Configuration().apply {
                allowSameOrigin = false
                anyHost()
            }
        )

        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("hyp-hen://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("plus+://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("do.t://host", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("digits11://host", dummyPoint()))

        assertEquals(OriginCheckResult.SkipCORS, plugin.checkOrigin("a()://host", dummyPoint()))
        assertEquals(OriginCheckResult.SkipCORS, plugin.checkOrigin("1abc://host", dummyPoint()))
    }

    @Test
    fun originWithWildcard() {
        val plugin = CORS(
            CORS.Configuration().apply {
                allowSameOrigin = true
                host("domain.com")
                host("*.domain.com")
            }
        )

        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://www.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://foo.bar.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.Failed, plugin.checkOrigin("http://domain.net", dummyPoint()))
        assertEquals(OriginCheckResult.Failed, plugin.checkOrigin("https://domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.Failed, plugin.checkOrigin("https://www.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.Failed, plugin.checkOrigin("https://foo.bar.domain.com", dummyPoint()))
    }

    @Test
    fun originWithWildcardAndSubdomain() {
        val plugin = CORS(
            CORS.Configuration().apply {
                allowSameOrigin = true
                host("domain.com", subDomains = listOf("foo", "*.bar"))
            }
        )

        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://foo.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://foo.bar.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.OK, plugin.checkOrigin("http://anything.bar.domain.com", dummyPoint()))
        assertEquals(OriginCheckResult.Failed, plugin.checkOrigin("http://invalid.foo.domain.com", dummyPoint()))
    }

    @Test
    fun invalidOriginWithWildcard() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            ("domain.com*" to messageWildcardInFrontOfDomain),
            ("domain.com*." to messageWildcardInFrontOfDomain),
            ("*." to messageWildcardInFrontOfDomain),
            ("**" to messageWildcardInFrontOfDomain),
            ("*.*." to messageWildcardInFrontOfDomain),
            ("*.*.domain.com" to messageWildcardOnlyOnce),
            ("*.foo*.domain.com" to messageWildcardOnlyOnce),
        ).forEach { (host, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for this host '$host'"
            ) {
                CORS(CORS.Configuration().apply { host(host) })
            }

            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun originWithWildcardAndSubDomain() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("foo*.") to messageWildcardInFrontOfDomain),
            (listOf("*.foo*.bar") to messageWildcardOnlyOnce),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                CORS(CORS.Configuration().apply { host("domain.com", subDomains = subDomains) })
            }

            assertEquals(expectedMessage, exception.message)
        }
    }

    @Test
    fun invalidOriginWithWildcardAndSubDomain() {
        val messageWildcardInFrontOfDomain = "wildcard must appear in front of the domain, e.g. *.domain.com"
        val messageWildcardOnlyOnce = "wildcard cannot appear more than once"

        listOf(
            (listOf("*.foo") to messageWildcardOnlyOnce),
            (listOf("*") to messageWildcardInFrontOfDomain),
            (listOf("foo") to messageWildcardInFrontOfDomain),
        ).forEach { (subDomains, expectedMessage) ->
            val exception = assertFailsWith<IllegalArgumentException>(
                "Expected this message '$expectedMessage' for sub domains $subDomains"
            ) {
                CORS(CORS.Configuration().apply { host("*.domain.com", subDomains = subDomains) })
            }

            assertEquals(expectedMessage, exception.message)
        }
    }

    private fun dummyPoint(): RequestConnectionPoint {
        return getConnectionPoint("scheme", "host", 12345)
    }

    private fun getConnectionPoint(scheme: String, host: String, port: Int): RequestConnectionPoint {
        return object : RequestConnectionPoint {
            override val scheme: String
                get() = scheme

            override val version: String
                get() = TODO("Is not supported in mock implementation")

            override val port: Int
                get() = port

            override val host: String
                get() = host

            override val uri: String
                get() = TODO("Is not supported in mock implementation")

            override val method: HttpMethod
                get() = TODO("Is not supported in mock implementation")

            override val remoteHost: String
                get() = TODO("Is not supported in mock implementation")
        }
    }
}
