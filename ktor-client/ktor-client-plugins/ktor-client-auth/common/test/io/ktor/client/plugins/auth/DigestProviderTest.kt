/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.util.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DigestProviderTest {
    private val path = "path"

    private val paramName = "param"

    private val paramValue = "value"

    private val authAllFields = """
        Digest
        algorithm=MD5,
        username="username",
        realm="realm",
        nonce="nonce",
        qop=qop,
        cnonce="client-nonce",
        uri="requested-uri",
        request="client-digest",
        message="message-digest",
        opaque="opaque"
    """.normalize()

    private val authMissingQopAndOpaque = """
        Digest
        algorithm=MD5,
        username="username",
        realm="realm",
        nonce="nonce",
        cnonce="client-nonce",
        uri="requested-uri",
        request="client-digest",
        message="message-digest"
    """.normalize()

    private val digestAuthProvider by lazy {
        DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
    }

    private lateinit var requestBuilder: HttpRequestBuilder

    @BeforeTest
    fun setup() {
        if (!PlatformUtils.IS_JVM) return
        val params = ParametersBuilder(1)
        params.append(paramName, paramValue)

        val url = URLBuilder(parameters = params.build(), trailingQuery = true).apply { encodedPath = path }
        requestBuilder = HttpRequestBuilder {
            takeFrom(url)
        }
    }

    @Test
    fun addRequestHeadersSetsExpectedAuthHeaderFields() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        runIsApplicable(authAllFields)
        val authHeader = addRequestHeaders(authAllFields)

        authHeader.assertParameter("qop", expectedValue = "qop")
        authHeader.assertParameter("opaque", expectedValue = "opaque".quote())
        authHeader.checkStandardParameters()
    }

    @Test
    fun addRequestHeadersMissingRealm() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        @Suppress("DEPRECATION_ERROR")
        val providerWithoutRealm = DigestAuthProvider("username", "pass", null)
        val authHeader = parseAuthorizationHeader(authAllFields)!!

        assertTrue(providerWithoutRealm.isApplicable(authHeader))
        providerWithoutRealm.addRequestHeaders(requestBuilder, authHeader)

        val resultAuthHeader = requestBuilder.headers[HttpHeaders.Authorization]!!
        resultAuthHeader.checkStandardParameters()
    }

    @Test
    fun addRequestHeadersChangedRealm() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        @Suppress("DEPRECATION_ERROR")
        val providerWithoutRealm = DigestAuthProvider("username", "pass", "wrong!")
        val authHeader = parseAuthorizationHeader(authAllFields)!!

        assertFalse(providerWithoutRealm.isApplicable(authHeader))
    }

    @Test
    fun addRequestHeadersOmitsQopAndOpaqueWhenMissing() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        runIsApplicable(authMissingQopAndOpaque)
        val authHeader = addRequestHeaders(authMissingQopAndOpaque)

        authHeader.assertParameterNotSet("opaque")
        authHeader.assertParameterNotSet("qop")
        authHeader.checkStandardParameters()
    }

    @Test
    fun testTokenWhenMissingRealmAndQop() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        @Suppress("DEPRECATION_ERROR")
        val providerWithoutRealm = DigestAuthProvider("username", "pass", null)
        val authHeader = parseAuthorizationHeader(authMissingQopAndOpaque)!!

        assertTrue(providerWithoutRealm.isApplicable(authHeader))
        providerWithoutRealm.addRequestHeaders(requestBuilder, authHeader)

        val resultAuthHeader = requestBuilder.headers[HttpHeaders.Authorization]!!
        val response = (parseAuthorizationHeader(resultAuthHeader) as HttpAuthHeader.Parameterized)
            .parameter("response")!!
        assertEquals("d51dd4b72db592e321b80d006d24c34c", response)
    }

    private fun runIsApplicable(headerValue: String) =
        digestAuthProvider.isApplicable(parseAuthorizationHeader(headerValue)!!)

    private suspend fun addRequestHeaders(headerValue: String): String {
        digestAuthProvider.addRequestHeaders(requestBuilder, parseAuthorizationHeader(headerValue)!!)
        return requestBuilder.headers[HttpHeaders.Authorization]!!
    }

    private fun String.checkStandardParameters() {
        assertParameter("realm", expectedValue = "realm".quote())
        assertParameter("username", expectedValue = "username".quote())
        assertParameter("nonce", expectedValue = "nonce".quote())
        assertParameter("nc", expectedValue = "00000001")
        assertParameter("uri", expectedValue = "/$path?$paramName=$paramValue".quote())
    }

    private fun String.assertParameter(name: String, expectedValue: String?) {
        assertContains(this, "$name=$expectedValue")
    }

    private fun String.assertParameterNotSet(name: String) {
        assertFalse(this.contains("$name="))
    }

    private fun String.normalize(): String = trimIndent().replace("\n", " ")
}
