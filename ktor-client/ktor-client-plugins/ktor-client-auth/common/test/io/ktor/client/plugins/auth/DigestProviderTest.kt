/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.ktor.client.plugins.auth

import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.test.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
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
        qop="auth,auth-int",
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
    fun `isApplicable rejects challenge whose algorithm does not match the configured one`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val sha512Challenge =
            parseAuthorizationHeader("""Digest algorithm=SHA-512-256, realm="realm", nonce="sha-nonce"""")
        assertNotNull(sha512Challenge)
        assertFalse(digestAuthProvider.isApplicable(sha512Challenge))
    }

    @Test
    fun `isApplicable accepts challenge with no algorithm parameter treating it as MD5`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val legacyChallenge = parseAuthorizationHeader("""Digest realm="realm", nonce="legacy-nonce"""")
        assertNotNull(legacyChallenge)
        assertTrue(digestAuthProvider.isApplicable(legacyChallenge))
    }

    @Test
    fun `client uses nonce from the algorithm-matching challenge not from the first challenge`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")

        val sha512Challenge =
            parseAuthorizationHeader("""Digest algorithm=SHA-512-256, realm="realm", nonce="sha-nonce"""")
        assertNotNull(sha512Challenge)
        assertFalse(provider.isApplicable(sha512Challenge))

        val md5Challenge =
            parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")
        assertNotNull(md5Challenge)
        assertTrue(provider.isApplicable(md5Challenge))

        provider.addRequestHeaders(requestBuilder, md5Challenge)

        val header = assertNotNull(requestBuilder.headers[HttpHeaders.Authorization])
        assertContains(header, """nonce="md5-nonce"""")
    }

    @Test
    fun `addRequestHeaders signs with the passed challenge and keeps no challenge state`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        val first = parseAuthorizationHeader(
            """Digest realm="realm", nonce="first-nonce", opaque="first-opaque", qop=auth"""
        )
        val second = parseAuthorizationHeader("""Digest realm="realm", nonce="second-nonce", opaque="second-opaque"""")
        assertNotNull(first)
        assertNotNull(second)

        // Two concurrent requests received their 401 responses before either of them retried
        assertTrue(provider.isApplicable(first))
        assertTrue(provider.isApplicable(second))

        provider.addRequestHeaders(requestBuilder, first)

        val header = assertNotNull(requestBuilder.headers[HttpHeaders.Authorization])
        assertContains(header, """nonce="first-nonce"""")
        assertContains(header, """opaque="first-opaque"""")
        assertContains(header, "qop=auth")

        // Accepted challenges are not stored, so there is nothing to sign without a challenge
        val retry = HttpRequestBuilder()
        provider.addRequestHeaders(retry, null)
        assertNull(retry.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders does not add header when no digest challenge was received`() = runTest {
        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") })
        val request = HttpRequestBuilder()
        val basicChallenge = parseAuthorizationHeader("""Basic realm="realm"""")
        assertNotNull(basicChallenge)

        assertFalse(provider.isApplicable(basicChallenge))
        provider.addRequestHeaders(request, basicChallenge)
        provider.addRequestHeaders(request, null)

        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `nonce counts are not evicted by fresh nonces of other protection spaces`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") })
        val server = "https://first.example/"
        val longLived =
            assertNotNull(parseAuthorizationHeader("""Digest realm="realm", nonce="long-lived", qop=auth"""))
        assertEquals("00000001", provider.nonceCountFor(longLived, url = server))

        // Another server and another realm of the same server issue a fresh nonce with every challenge
        repeat(100) { index ->
            val otherServer = parseAuthorizationHeader("""Digest realm="realm", nonce="server-$index", qop=auth""")
            assertEquals(
                "00000001",
                provider.nonceCountFor(assertNotNull(otherServer), url = "https://second.example/")
            )

            val otherRealm = parseAuthorizationHeader("""Digest realm="other-realm", nonce="realm-$index", qop=auth""")
            assertEquals("00000001", provider.nonceCountFor(assertNotNull(otherRealm), url = server))
        }

        assertEquals("00000002", provider.nonceCountFor(longLived, url = server))
    }

    @Test
    fun `nonce counts are kept for several nonces used at once in one protection space`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        // For example, load-balanced backends that each issue their own long-lived nonce
        val challenges = (1..8).map { index ->
            assertNotNull(parseAuthorizationHeader("""Digest realm="realm", nonce="backend-$index", qop=auth"""))
        }

        repeat(3) { round ->
            val expected = (round + 1).toString(radix = 16).padStart(length = 8, padChar = '0')
            challenges.forEach { assertEquals(expected, provider.nonceCountFor(it)) }
        }
    }

    @Test
    fun `concurrent requests with the same nonce get distinct nonce counts`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        val challenge = assertNotNull(parseAuthorizationHeader("""Digest realm="realm", nonce="nonce", qop=auth"""))
        val requests = 100

        val nonceCounts = withContext(Dispatchers.Default) {
            List(requests) { async { provider.nonceCountFor(challenge) } }.awaitAll()
        }

        val expected = (1..requests).map { it.toString(radix = 16).padStart(length = 8, padChar = '0') }
        assertEquals(expected.toSet(), nonceCounts.toSet())
    }

    @Test
    fun addRequestHeadersSetsExpectedAuthHeaderFields() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        runIsApplicable(authAllFields)
        val authHeader = addRequestHeaders(authAllFields)

        authHeader.assertParameter("qop", expectedValue = "auth")
        authHeader.assertParameter("nc", expectedValue = "00000001")
        authHeader.assertParameter("opaque", expectedValue = "opaque".quote())
        authHeader.checkStandardParameters()
    }

    @Test
    fun `client chooses auth from the offered qop values`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") }, "realm")
        val challenge = parseAuthorizationHeader("""Digest realm="realm", nonce="nonce", qop="auth-int, auth"""")
        assertNotNull(challenge)
        assertTrue(provider.isApplicable(challenge))

        provider.addRequestHeaders(requestBuilder, challenge)

        val header = assertNotNull(requestBuilder.headers[HttpHeaders.Authorization])
        val parameters = parseAuthorizationHeader(header) as HttpAuthHeader.Parameterized
        assertEquals("auth", parameters.parameter("qop"))
        assertEquals(expectedResponse(parameters, password = "password"), parameters.parameter("response"))
    }

    @Test
    fun `isApplicable rejects challenge offering only unsupported qop values`() {
        val provider = DigestAuthProvider({ DigestAuthCredentials("username", "password") })
        val challenge = parseAuthorizationHeader("""Digest realm="realm", nonce="nonce", qop="auth-int"""")
        assertNotNull(challenge)

        assertFalse(provider.isApplicable(challenge))
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
        authHeader.assertParameterNotSet("nc")
        authHeader.assertParameterNotSet("cnonce")
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

    @Test
    fun `empty path treated as slash for uri and hash`() = runTest {
        if (!PlatformUtils.IS_JVM) return@runTest

        val provider = DigestAuthProvider(
            credentials = { DigestAuthCredentials("username", "pass") },
            realm = null
        )
        val authHeader = parseAuthorizationHeader(authMissingQopAndOpaque)!!

        val expectedResponses = mapOf(
            "" to "59371c611ceaf1d6d76c6da3136e7f59",
            "?x=1" to "1313e773fecf0751fc8222d164931909",
        )

        for (queryParameters in expectedResponses.keys) {
            val pathRequest = HttpRequestBuilder {
                takeFrom("https://example.com$queryParameters")
            }

            assertTrue(provider.isApplicable(authHeader))
            provider.addRequestHeaders(pathRequest, authHeader)

            val headerValue = checkNotNull(pathRequest.headers[HttpHeaders.Authorization])
            val resultAuthHeader = parseAuthorizationHeader(headerValue) as HttpAuthHeader.Parameterized
            assertEquals("/$queryParameters", resultAuthHeader.parameter("uri"))
            assertEquals(expectedResponses[queryParameters], resultAuthHeader.parameter("response"))
        }
    }

    private suspend fun DigestAuthProvider.nonceCountFor(
        challenge: HttpAuthHeader,
        url: String = "http://localhost/",
    ): String? {
        val request = HttpRequestBuilder { takeFrom(url) }
        addRequestHeaders(request, challenge)
        val rawHeader = assertNotNull(request.headers[HttpHeaders.Authorization])
        val header = parseAuthorizationHeader(rawHeader) as HttpAuthHeader.Parameterized
        return header.parameter("nc")
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
        assertParameter("uri", expectedValue = "/$path?$paramName=$paramValue".quote())
    }

    private suspend fun expectedResponse(parameters: HttpAuthHeader.Parameterized, password: String): String {
        fun parameter(name: String) = assertNotNull(parameters.parameter(name))

        val ha1 = md5Hex("${parameter("username")}:${parameter("realm")}:$password")
        val ha2 = md5Hex("GET:${parameter("uri")}")
        val tokens = listOf(ha1, parameter("nonce"), parameter("nc"), parameter("cnonce"), parameter("qop"), ha2)
        return md5Hex(tokens.joinToString(":"))
    }

    @OptIn(InternalAPI::class)
    private suspend fun md5Hex(data: String): String = Digest("MD5").build(data.encodeToByteArray()).toHexString()

    private fun String.assertParameter(name: String, expectedValue: String?) {
        assertContains(this, "$name=$expectedValue")
    }

    private fun String.assertParameterNotSet(name: String) {
        assertFalse(this.contains("$name="))
    }

    private fun String.normalize(): String = trimIndent().replace("\n", " ")
}
