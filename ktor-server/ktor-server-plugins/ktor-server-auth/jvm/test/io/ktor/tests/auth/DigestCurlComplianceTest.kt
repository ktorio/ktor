/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Black Box Compliance Test Suite for Server Digest Authentication.
 *
 * These tests verify that Ktor's implementation correctly interoperates with the curl client, ensuring compliance with:
 * - RFC 7616 (HTTP Digest Access Authentication) - Modern standard with SHA-256/SHA-512-256
 * - RFC 2617 (HTTP Authentication) - Legacy standard with MD5 for backward compatibility
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DigestCurlComplianceTest {

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

        private var curlVersion: Pair<Int, Int>? = null
    }

    private fun getCurlVersion(): Pair<Int, Int>? = runCatching {
        val process = ProcessBuilder("curl", "--version")
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()

        val versionRegex = """curl (\d+)\.(\d+)\.(\d+)""".toRegex()
        val match = versionRegex.find(output) ?: return@runCatching null
        val (major, minor, _) = match.destructured
        major.toInt() to minor.toInt()
    }.getOrDefault(null)

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

    @BeforeAll
    fun setupServer() {
        curlVersion = getCurlVersion() ?: return
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

    data class CurlResult(
        val stdout: String,
        val stderr: String
    ) {
        /**
         * Extracts HTTP response headers from curl's verbose output.
         * Curl outputs headers to stderr when using a -v flag, prefixed with "<"
         */
        fun getResponseHeaders(): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            stderr.lines()
                .filter { it.startsWith("< ") }
                .map { it.removePrefix("< ").trim() }
                .filter { it.contains(":") }
                .forEach { line ->
                    val colonIndex = line.indexOf(":")
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                }
            return headers
        }

        /**
         * Extracts HTTP request headers from curl's verbose output.
         * Curl outputs request headers to stderr when using a -v flag, prefixed with ">"
         */
        fun getRequestHeaders(): Map<String, String> {
            val headers = mutableMapOf<String, String>()
            stderr.lines()
                .filter { it.startsWith("> ") }
                .map { it.removePrefix("> ").trim() }
                .filter { it.contains(":") }
                .forEach { line ->
                    val colonIndex = line.indexOf(":")
                    val key = line.substring(0, colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim()
                    headers[key] = value
                }
            return headers
        }

        fun getStatusCode(): Int? {
            val statusLine = stderr.lines()
                .lastOrNull { it.trimStart().startsWith("< HTTP/") } ?: return null

            return statusLine.trimStart().removePrefix("< HTTP/").split(" ")
                .getOrNull(1)?.toIntOrNull()
        }

        fun getAuthorizationHeader(): String = getRequestHeaders()["Authorization"]
            ?: fail("Authorization header not found in response")

        fun getWwwAuthenticateHeaders(): List<String> {
            return stderr.lines()
                .filter { it.startsWith("< WWW-Authenticate:") }
                .map { it.removePrefix("< WWW-Authenticate:").trim() }
        }
    }

    /**
     * Executes a curl command and captures its output.
     */
    private fun runCurl(
        url: String,
        options: List<String> = emptyList(),
        timeout: Duration = 30.seconds,
        minimumVersion: Pair<Int, Int>? = null
    ): CurlResult {
        val version = curlVersion
        assertTrue(version != null, "curl is not available in PATH")
        minimumVersion?.let { (major, minor) ->
            val ok = version.first > major || (version.first == major && version.second >= minor)
            assertTrue(ok, "curl version must be >= $major.$minor got ${version.first}.${version.second}")
        }

        val command = mutableListOf("curl", "-v")
        command.addAll(options)
        command.add(url)

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()

        val completed = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw AssertionError("curl command timed out after $timeout")
        }
        assertEquals(0, process.exitValue(), "curl should exit successfully")

        return CurlResult(stdout, stderr)
    }

    private fun runDigestCurl(
        path: String,
        user: String = TEST_USER,
        password: String = TEST_PASS,
        extraOptions: List<String> = emptyList(),
        expectSuccess: Boolean = true,
        minimumVersion: Pair<Int, Int>? = null
    ): CurlResult {
        val options = mutableListOf("--digest", "--user", "$user:$password")
        options.addAll(extraOptions)
        val result = runCurl("http://127.0.0.1:$serverPort$path", options, minimumVersion = minimumVersion)
        if (expectSuccess) {
            assertEquals(200, result.getStatusCode(), "Server should return 200 OK")
        } else {
            assertEquals(401, result.getStatusCode(), "Server should return 401 Unauthorized")
        }
        return result
    }

    @Test
    fun testSHA256Negotiation() {
        val result = runDigestCurl("/sha256")
        assertContains(result.stdout, "OK - SHA-256", message = "Response body should confirm SHA-256")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "algorithm=SHA-256")
    }

    @Test
    fun testSHA512_256Negotiation() {
        val result = runDigestCurl("/sha512-256", minimumVersion = 8 to 7)
        assertContains(result.stdout, "OK - SHA-512-256", message = "Response body should confirm SHA-512-256")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "algorithm=SHA-512-256")
    }

    @Test
    fun testUserHashAdvertisedInChallenge() {
        val challengeResult = runCurl("http://127.0.0.1:$serverPort/userhash")
        assertEquals(401, challengeResult.getStatusCode(), "Should return 401 Unauthorized")

        val wwwAuthHeaders = challengeResult.getWwwAuthenticateHeaders()
        assertTrue(wwwAuthHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challengeWithUserHash = wwwAuthHeaders.any { it.contains("userhash=true") }
        assertTrue(challengeWithUserHash, "Server should advertise userhash=true in challenge")
    }

    @Test
    fun testUserHashAuthentication() {
        val result = runDigestCurl("/userhash")
        assertContains(result.stdout, "OK - UserHash", message = "Response body should confirm userhash endpoint")
    }

    @Test
    fun testABNFHeaderCompliance() {
        val result = runCurl("http://127.0.0.1:$serverPort/sha256")
        assertEquals(401, result.getStatusCode(), "Should return 401 Unauthorized")

        val wwwAuthHeaders = result.getWwwAuthenticateHeaders()
        assertTrue(wwwAuthHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = wwwAuthHeaders.first()

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
        val result = runCurl("http://127.0.0.1:$serverPort/md5")
        assertEquals(401, result.getStatusCode(), "Should return 401 Unauthorized")

        val wwwAuthHeaders = result.getWwwAuthenticateHeaders()
        assertTrue(wwwAuthHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = wwwAuthHeaders.first()

        assertTrue(
            challenge.contains(Regex("""algorithm=MD5(?![^,]*")""")),
            "algorithm should be unquoted token: $challenge"
        )
    }

    @Test
    fun testSHA256SessionAlgorithm() {
        val result = runDigestCurl("/sha256-sess")
        assertContains(result.stdout, "OK - SHA-256-sess", message = "Response should confirm session algorithm")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "algorithm=SHA-256-sess")
    }

    @Test
    fun testMD5SessionAlgorithm() {
        val result = runDigestCurl("/md5-sess")
        assertContains(result.stdout, "OK - MD5-sess", message = "Response should confirm session algorithm")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "algorithm=MD5-sess")
    }

    @Test
    fun testAuthIntWithGet() {
        val result = runDigestCurl("/auth-int")
        assertContains(result.stdout, "OK - Auth-Int", message = "Response should confirm auth-int")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "qop=auth-int")
    }

    @Test
    fun testAuthBothQopAdvertised() {
        val result = runCurl("http://127.0.0.1:$serverPort/auth-both")
        assertEquals(401, result.getStatusCode(), "Should return 401 Unauthorized")

        val wwwAuthHeaders = result.getWwwAuthenticateHeaders()
        assertTrue(wwwAuthHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = wwwAuthHeaders.first()
        assertTrue(challenge.contains("auth") && challenge.contains("auth-int"))
    }

    @Test
    fun testMD5BackwardCompatibility() {
        val result = runDigestCurl("/md5")
        assertContains(result.stdout, "OK - MD5", message = "Response body should confirm MD5")

        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "algorithm=MD5")
    }

    @Test
    fun testMultipleAlgorithmChallenge() {
        val challengeResult = runCurl("http://127.0.0.1:$serverPort/multi-algo")
        assertEquals(401, challengeResult.getStatusCode(), "Should return 401 Unauthorized")
        val wwwAuthHeaders = challengeResult.getWwwAuthenticateHeaders()

        val hasSHA256 = wwwAuthHeaders.any { it.contains("algorithm=SHA-256") }
        val hasMD5 = wwwAuthHeaders.any { it.contains("algorithm=MD5") }

        assertTrue(hasSHA256, "Server should offer SHA-256 challenge")
        assertTrue(hasMD5, "Server should offer MD5 challenge")
    }

    @Test
    fun testCurlAuthenticatesWithMultipleAlgorithmsOffered() {
        val result = runDigestCurl("/multi-algo")
        val authHeader = result.getAuthorizationHeader()
        val usedSHA256 = authHeader.contains("algorithm=SHA-256")
        val usedMD5 = authHeader.contains("algorithm=MD5")
        assertTrue(usedSHA256 || usedMD5, "curl should use one of the offered algorithms (SHA-256 or MD5)")
    }

    @Test
    fun testAuthenticationInfoHeader() {
        val result = runDigestCurl("/sha256")

        val responseHeaders = result.getResponseHeaders()
        val authInfo = responseHeaders["Authentication-Info"]

        assertNotNull(authInfo, "Authentication-Info header should be present on successful auth")

        assertContains(authInfo, "rspauth=", message = "Authentication-Info should contain rspauth")
        assertContains(authInfo, "qop=", message = "Authentication-Info should contain qop")
        assertContains(authInfo, "nc=", message = "Authentication-Info should contain nc")
        assertContains(authInfo, "cnonce=", message = "Authentication-Info should contain cnonce")
        assertContains(authInfo, "nextnonce=", message = "Authentication-Info should contain nextnonce")
    }

    @Test
    fun testAuthenticationInfoWithSessionAlgorithm() {
        val result = runDigestCurl("/sha256-sess")
        val responseHeaders = result.getResponseHeaders()
        val authInfo = responseHeaders["Authentication-Info"]
        assertNotNull(authInfo, "Authentication-Info header should be present")
        assertContains(authInfo, "rspauth=", message = "rspauth should be present for session algorithm")
    }

    @Test
    fun testUTF8CharsetAdvertised() {
        val result = runCurl("http://127.0.0.1:$serverPort/utf8")
        assertEquals(401, result.getStatusCode(), "Should return 401 Unauthorized")

        val wwwAuthHeaders = result.getWwwAuthenticateHeaders()
        assertTrue(wwwAuthHeaders.isNotEmpty(), "WWW-Authenticate header should be present")

        val challenge = wwwAuthHeaders.first()
        assertContains(challenge, "charset=UTF-8")
    }

    @Test
    fun testUTF8Authentication() {
        val result = runDigestCurl("/utf8")
        assertContains(result.stdout, "OK - UTF-8", message = "Response should confirm UTF-8 endpoint")
    }

    @Test
    fun testAuthenticationFails() {
        runDigestCurl("/sha256", password = "wrong_password", expectSuccess = false)
        runDigestCurl("/sha256", user = "unknown_user", expectSuccess = false)
    }

    @Test
    fun testNonceAndCnonceInResponse() {
        val result = runDigestCurl("/sha256")
        val authHeader = result.getAuthorizationHeader()
        assertContains(authHeader, "nc=", message = "nc (nonce count) should be present when qop is used")
        assertContains(authHeader, "cnonce=", message = "cnonce (client nonce) should be present when qop is used")
        assertContains(authHeader, "qop=auth", message = "qop should be present")
    }
}
