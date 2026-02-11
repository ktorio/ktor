/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.apache.hc.client5.http.auth.AuthScope
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.HttpRequestInterceptor
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.*

/**
 * Black Box Compliance Test Suite for Server Digest Authentication.
 *
 * These tests verify that Ktor's implementation correctly interoperates with Apache HTTP Client,
 * ensuring compliance with:
 * - RFC 7616 (HTTP Digest Access Authentication) - Modern standard with SHA-256/SHA-512-256
 * - RFC 2617 (HTTP Authentication) - Legacy standard with MD5 for backward compatibility
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DigestApacheClientIntegrationTest {

    @BeforeAll
    fun setupServer() {
        serverPort = findFreePort()

        val basicProvider: DigestProviderFunctionV2 = { userName, providerRealm, algorithm ->
            users[userName]?.let { password ->
                computeHA1(userName, providerRealm, password, algorithm)
            }
        }

        @Suppress("DEPRECATION")
        val localServer = embeddedServer(Netty, port = serverPort) {
            install(Authentication) {
                digest("sha256") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("sha512-256") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_512_256)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("md5") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.MD5)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("multi-algo") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.MD5)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("sha256-sess") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256_SESS)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("md5-sess") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.MD5_SESS)
                    supportedQop = listOf(DigestQop.AUTH)
                    digestProvider(basicProvider)
                }
                digest("auth-int") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256)
                    supportedQop = listOf(DigestQop.AUTH_INT)
                    digestProvider(basicProvider)
                }
                digest("auth-both") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256)
                    supportedQop = listOf(DigestQop.AUTH, DigestQop.AUTH_INT)
                    digestProvider(basicProvider)
                }
                digest("userhash") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256)
                    supportedQop = listOf(DigestQop.AUTH)
                    userHashResolver { userHash, resolverRealm, algorithm ->
                        users.keys.find {
                            computeUserHash(it, resolverRealm, algorithm) == userHash
                        }
                    }
                    digestProvider(basicProvider)
                }
                digest("utf8") {
                    realm = TEST_REALM
                    algorithms = listOf(DigestAlgorithm.SHA_256)
                    supportedQop = listOf(DigestQop.AUTH)
                    charset = Charsets.UTF_8
                    digestProvider(basicProvider)
                }
            }

            routing {
                authenticate("sha256") {
                    get("/sha256") { call.respondText("OK - SHA-256") }
                }
                authenticate("sha512-256") {
                    get("/sha512-256") { call.respondText("OK - SHA-512-256") }
                }
                authenticate("md5") {
                    get("/md5") { call.respondText("OK - MD5") }
                }
                authenticate("multi-algo") {
                    get("/multi-algo") { call.respondText("OK - Multi Algorithm") }
                }
                authenticate("sha256-sess") {
                    get("/sha256-sess") { call.respondText("OK - SHA-256-sess") }
                }
                authenticate("md5-sess") {
                    get("/md5-sess") { call.respondText("OK - MD5-sess") }
                }
                authenticate("auth-int") {
                    get("/auth-int") { call.respondText("OK - Auth-Int") }
                }
                authenticate("auth-both") {
                    get("/auth-both") { call.respondText("OK - Auth Both") }
                }
                authenticate("userhash") {
                    get("/userhash") { call.respondText("OK - UserHash") }
                }
                authenticate("utf8") {
                    get("/utf8") { call.respondText("OK - UTF-8") }
                }
            }
        }

        server = localServer.also { it.start() }
    }

    @AfterAll
    fun teardownServer() {
        server?.stop(100, 500, TimeUnit.MILLISECONDS)
    }

    data class HttpResult(
        val statusCode: Int,
        val body: String,
        val authorizationHeader: String?,
        val wwwAuthenticateHeaders: List<String>,
        val authenticationInfoHeader: String?
    )

    /**
     * Executes an HTTP request with digest authentication using Apache HTTP Client.
     */
    private fun executeDigestRequest(
        path: String,
        user: String = TEST_USER,
        password: String = TEST_PASS,
        expectSuccess: Boolean = true
    ): HttpResult {
        val credentials = UsernamePasswordCredentials(user, password.toCharArray())

        val credentialsProvider = CredentialsProviderBuilder.create()
            .add(AuthScope("127.0.0.1", serverPort), credentials)
            .build()

        var capturedAuthHeader: String? = null

        val requestInterceptor = HttpRequestInterceptor { request, _, _ ->
            capturedAuthHeader = request.getHeader("Authorization")?.value
        }

        HttpClients.custom()
            .setDefaultCredentialsProvider(credentialsProvider)
            .addRequestInterceptorLast(requestInterceptor)
            .build()
            .use { client ->
                val httpGet = HttpGet("http://127.0.0.1:$serverPort$path")

                return client.execute(httpGet) { response ->
                    val statusCode = response.code
                    val body = EntityUtils.toString(response.entity)
                    val wwwAuthHeaders = response.getHeaders("WWW-Authenticate").map { it.value }
                    val authInfoHeader = response.getHeader("Authentication-Info")?.value
                    if (expectSuccess) {
                        assertEquals(200, statusCode, "Server should return 200 OK")
                    } else {
                        assertEquals(401, statusCode, "Server should return 401 Unauthorized")
                    }
                    HttpResult(statusCode, body, capturedAuthHeader, wwwAuthHeaders, authInfoHeader)
                }
            }
    }

    /**
     * Executes an HTTP request without authentication to get the challenge.
     */
    private fun executeUnauthenticatedRequest(path: String): HttpResult {
        HttpClients.createDefault().use { client ->
            val httpGet = HttpGet("http://127.0.0.1:$serverPort$path")

            return client.execute(httpGet) { response ->
                val statusCode = response.code
                val body = EntityUtils.toString(response.entity)
                val wwwAuthHeaders = response.getHeaders("WWW-Authenticate").map { it.value }
                val authInfoHeader = response.getHeader("Authentication-Info")?.value

                HttpResult(statusCode, body, null, wwwAuthHeaders, authInfoHeader)
            }
        }
    }

    @Test
    fun testSHA256Negotiation() {
        val result = executeDigestRequest("/sha256")
        assertContains(result.body, "OK - SHA-256", message = "Response body should confirm SHA-256")

        // Apache HTTP Client should have used SHA-256 algorithm
        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "algorithm=SHA-256")
    }

    @Test
    fun testSHA512_256Negotiation() {
        val result = executeDigestRequest("/sha512-256")
        assertContains(result.body, "OK - SHA-512-256", message = "Response body should confirm SHA-512-256")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "algorithm=SHA-512-256")
    }

    @Test
    fun testUserHashAdvertisedInChallenge() {
        val result = executeUnauthenticatedRequest("/userhash")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")

        assertTrue(result.wwwAuthenticateHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challengeWithUserHash = result.wwwAuthenticateHeaders.any { it.contains("userhash=true") }
        assertTrue(challengeWithUserHash, "Server should advertise userhash=true in challenge")
    }

    @Test
    @Ignore // TODO wait until apache fully supports username
    fun testUserHashAuthentication() {
        val result = executeDigestRequest("/userhash")
        assertContains(result.body, "OK - UserHash", message = "Response body should confirm userhash endpoint")
    }

    @Test
    fun testABNFHeaderCompliance() {
        val result = executeUnauthenticatedRequest("/sha256")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")
        assertTrue(result.wwwAuthenticateHeaders.isNotEmpty(), "WWW-Authenticate header should be present")
        val challenge = result.wwwAuthenticateHeaders.first()

        assertTrue(
            challenge.contains(Regex("""algorithm=SHA-256(?![^,]*")""")),
            "algorithm should be unquoted token: $challenge"
        )
        assertFalse(
            challenge.contains("algorithm=\"SHA-256\""),
            "algorithm MUST NOT be quoted per RFC 7616: $challenge"
        )

        assertTrue(
            challenge.contains(Regex("""realm="[^"]*"""")),
            "realm should be quoted string: $challenge"
        )

        assertTrue(
            challenge.contains(Regex("""nonce="[^"]*"""")),
            "nonce should be quoted string: $challenge"
        )

        assertTrue(
            challenge.contains(Regex("""qop="[^"]*"""")),
            "qop should be quoted string: $challenge"
        )
    }

    @Test
    fun testABNFHeaderComplianceMD5() {
        val result = executeUnauthenticatedRequest("/md5")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")

        assertTrue(result.wwwAuthenticateHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = result.wwwAuthenticateHeaders.first()

        assertTrue(
            challenge.contains(Regex("""algorithm=MD5(?![^,]*")""")),
            "algorithm should be unquoted token: $challenge"
        )
    }

    @Test
    fun testSHA256SessionAlgorithm() {
        val result = executeDigestRequest("/sha256-sess")
        assertContains(result.body, "OK - SHA-256-sess", message = "Response should confirm session algorithm")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "algorithm=SHA-256-sess")
    }

    @Test
    fun testMD5SessionAlgorithm() {
        val result = executeDigestRequest("/md5-sess")
        assertContains(result.body, "OK - MD5-sess", message = "Response should confirm session algorithm")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "algorithm=MD5-sess")
    }

    @Test
    fun testAuthIntWithGet() {
        val result = executeDigestRequest("/auth-int")
        assertContains(result.body, "OK - Auth-Int", message = "Response should confirm auth-int")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "qop=auth-int")
    }

    @Test
    fun testAuthBothQopAdvertised() {
        val result = executeUnauthenticatedRequest("/auth-both")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")

        assertTrue(result.wwwAuthenticateHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = result.wwwAuthenticateHeaders.first()
        assertTrue(challenge.contains("auth") && challenge.contains("auth-int"))
    }

    @Test
    fun testMD5BackwardCompatibility() {
        val result = executeDigestRequest("/md5")
        assertContains(result.body, "OK - MD5", message = "Response body should confirm MD5")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(result.authorizationHeader, "algorithm=MD5")
    }

    @Test
    fun testMultipleAlgorithmChallenge() {
        val result = executeUnauthenticatedRequest("/multi-algo")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")

        val hasSHA256 = result.wwwAuthenticateHeaders.any { it.contains("algorithm=SHA-256") }
        val hasMD5 = result.wwwAuthenticateHeaders.any { it.contains("algorithm=MD5") }

        assertTrue(hasSHA256, "Server should offer SHA-256 challenge")
        assertTrue(hasMD5, "Server should offer MD5 challenge")
    }

    @Test
    fun testClientAuthenticatesWithMultipleAlgorithmsOffered() {
        val result = executeDigestRequest("/multi-algo")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        val usedSHA256 = result.authorizationHeader.contains("algorithm=SHA-256")
        val usedMD5 = result.authorizationHeader.contains("algorithm=MD5")
        assertTrue(usedSHA256 || usedMD5, "Client should use one of the offered algorithms (SHA-256 or MD5)")
    }

    @Test
    fun testAuthenticationInfoHeader() {
        val result = executeDigestRequest("/sha256")

        assertNotNull(
            result.authenticationInfoHeader,
            "Authentication-Info header should be present on successful auth"
        )

        assertContains(
            result.authenticationInfoHeader,
            "rspauth=",
            message = "Authentication-Info should contain rspauth"
        )
        assertContains(result.authenticationInfoHeader, "qop=", message = "Authentication-Info should contain qop")
        assertContains(result.authenticationInfoHeader, "nc=", message = "Authentication-Info should contain nc")
        assertContains(
            result.authenticationInfoHeader,
            "cnonce=",
            message = "Authentication-Info should contain cnonce"
        )
        assertContains(
            result.authenticationInfoHeader,
            "nextnonce=",
            message = "Authentication-Info should contain nextnonce"
        )
    }

    @Test
    fun testAuthenticationInfoWithSessionAlgorithm() {
        val result = executeDigestRequest("/sha256-sess")

        assertNotNull(result.authenticationInfoHeader, "Authentication-Info header should be present")
        assertContains(
            result.authenticationInfoHeader,
            "rspauth=",
            message = "rspauth should be present for session algorithm"
        )
    }

    @Test
    fun testUTF8CharsetAdvertised() {
        val result = executeUnauthenticatedRequest("/utf8")
        assertEquals(401, result.statusCode, "Should return 401 Unauthorized")

        assertTrue(result.wwwAuthenticateHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = result.wwwAuthenticateHeaders.first()
        assertContains(challenge, "charset=UTF-8")
    }

    @Test
    fun testUTF8Authentication() {
        val result = executeDigestRequest("/utf8")
        assertContains(result.body, "OK - UTF-8", message = "Response should confirm UTF-8 endpoint")
    }

    @Test
    fun testAuthenticationFails() {
        executeDigestRequest("/sha256", password = "wrong_password", expectSuccess = false)
        executeDigestRequest("/sha256", user = "unknown_user", expectSuccess = false)
    }

    @Test
    fun testNonceAndCnonceInResponse() {
        val result = executeDigestRequest("/sha256")

        assertNotNull(result.authorizationHeader, "Authorization header should be present")
        assertContains(
            result.authorizationHeader,
            "nc=",
            message = "nc (nonce count) should be present when qop is used"
        )
        assertContains(
            result.authorizationHeader,
            "cnonce=",
            message = "cnonce (client nonce) should be present when qop is used"
        )
        assertContains(result.authorizationHeader, "qop=auth", message = "qop should be present")
    }

    private fun findFreePort(): Int = ServerSocket(0).use { socket -> socket.localPort }

    /**
     * Computes H(username:realm:password) for digest authentication.
     */
    private fun computeHA1(userName: String, realm: String, password: String, algorithm: DigestAlgorithm): ByteArray {
        val digester = algorithm.toDigester()
        return digester.digest("$userName:$realm:$password".toByteArray(Charsets.UTF_8))
    }

    /**
     * Computes the userhash: H(username:realm) for privacy protection.
     */
    private fun computeUserHash(username: String, realm: String, algorithm: DigestAlgorithm): String {
        val digester = algorithm.toDigester()
        return hex(digester.digest("$username:$realm".toByteArray(Charsets.UTF_8)))
    }

    companion object {
        private const val TEST_USER = "testuser"
        private const val TEST_PASS = "test_password"
        private const val TEST_REALM = "test-realm@ktor.io"

        private val users = mapOf(
            TEST_USER to TEST_PASS,
            "Mufasa" to "CircleOfLife"
        )

        private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
        private var serverPort: Int = 0
    }
}
