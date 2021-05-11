/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.auth

import io.ktor.client.features.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.test.dispatcher.*
import io.ktor.util.*
import kotlin.test.*

class DigestProviderTest {
    private val path = "path"

    private val paramName = "param"

    private val paramValue = "value"

    private val authAllFields =
        "Digest algorithm=MD5, username=\"username\", realm=\"realm\", nonce=\"nonce\", qop=\"qop\", " +
            "snonce=\"server-nonce\", cnonce=\"client-nonce\", uri=\"requested-uri\", " +
            "request=\"client-digest\", message=\"message-digest\", opaque=\"opaque\""

    private val authMissingQopAndOpaque =
        "Digest algorithm=MD5, username=\"username\", realm=\"realm\", nonce=\"nonce\", snonce=\"server-nonce\", " +
            "cnonce=\"client-nonce\", uri=\"requested-uri\", request=\"client-digest\", message=\"message-digest\""

    private val digestAuthProvider by lazy {
        DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
    }

    lateinit var requestBuilder: HttpRequestBuilder

    @BeforeTest
    fun setup() {
        if (!PlatformUtils.IS_JVM) return
        val params = ParametersBuilder(1)
        params.append(paramName, paramValue)

        val url = URLBuilder(encodedPath = path, parameters = params, trailingQuery = true)
        requestBuilder = HttpRequestBuilder {
            takeFrom(url)
        }
    }

    @Test
    fun addRequestHeadersSetsExpectedAuthHeaderFields() = testSuspend {
        if (!PlatformUtils.IS_JVM) return@testSuspend

        runIsApplicable(authAllFields)
        val authHeader = addRequestHeaders()

        assertTrue(authHeader.contains("qop=qop"))
        assertTrue(authHeader.contains("opaque=opaque"))
        checkStandardFields(authHeader)
    }

    @Test
    fun addRequestHeadersMissingRealm() = testSuspend {
        if (!PlatformUtils.IS_JVM) return@testSuspend

        val providerWithoutRealm = DigestAuthProvider("username", "pass", null)
        val authHeader = parseAuthorizationHeader(authAllFields)!!
        requestBuilder.attributes.put(AuthHeaderAttribute, authHeader)

        assertTrue(providerWithoutRealm.isApplicable(authHeader))
        providerWithoutRealm.addRequestHeaders(requestBuilder)

        val resultAuthHeader = requestBuilder.headers[HttpHeaders.Authorization]!!
        checkStandardFields(resultAuthHeader)
    }

    @Test
    fun addRequestHeadersChangedRealm() = testSuspend {
        if (!PlatformUtils.IS_JVM) return@testSuspend

        val providerWithoutRealm = DigestAuthProvider("username", "pass", "wrong!")
        val authHeader = parseAuthorizationHeader(authAllFields)!!
        requestBuilder.attributes.put(AuthHeaderAttribute, authHeader)

        assertFalse(providerWithoutRealm.isApplicable(authHeader))
    }

    @Test
    fun addRequestHeadersOmitsQopAndOpaqueWhenMissing() = testSuspend {
        if (!PlatformUtils.IS_JVM) return@testSuspend

        runIsApplicable(authMissingQopAndOpaque)
        val authHeader = addRequestHeaders()

        assertFalse(authHeader.contains("opaque="))
        assertFalse(authHeader.contains("qop="))
        checkStandardFields(authHeader)
    }

    private fun runIsApplicable(headerValue: String) =
        digestAuthProvider.isApplicable(parseAuthorizationHeader(headerValue)!!)

    private suspend fun addRequestHeaders(): String {
        digestAuthProvider.addRequestHeaders(requestBuilder)
        return requestBuilder.headers[HttpHeaders.Authorization]!!
    }

    private fun checkStandardFields(authHeader: String) {
        assertTrue(authHeader.contains("realm=realm"))
        assertTrue(authHeader.contains("username=username"))
        assertTrue(authHeader.contains("nonce=nonce"))

        val uriPattern = "uri=\"/$path?$paramName=$paramValue\""
        assertTrue(authHeader.contains(uriPattern))
    }
}
