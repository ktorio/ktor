/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.Charset
import java.security.MessageDigest
import kotlin.test.*
import kotlin.text.Charsets

/**
 * Tests for RFC 7616 (HTTP Digest Access Authentication) support.
 */
class DigestRFC7616Test {

    private fun computeHA1(
        userName: String,
        realm: String,
        password: String,
        algorithm: DigestAlgorithm = DigestAlgorithm.MD5,
        charset: Charset = Charsets.UTF_8
    ): ByteArray {
        val digester = algorithm.toDigester()
        digester.update("$userName:$realm:$password".toByteArray(charset))
        return digester.digest()
    }

    private fun digest(algorithm: DigestAlgorithm, data: String, charset: Charset = Charsets.UTF_8): ByteArray {
        val digester = algorithm.toDigester()
        digester.update(data.toByteArray(charset))
        return digester.digest()
    }

    private fun computeUserHash(username: String, realm: String, algorithm: DigestAlgorithm): String {
        val digester = algorithm.toDigester()
        digester.update("$username:$realm".toByteArray(Charsets.UTF_8))
        return hex(digester.digest())
    }

    private fun createCredential(
        realm: String,
        userName: String,
        digestUri: String,
        nonce: String,
        algorithm: String?,
        cnonce: String,
        nc: String = "00000001",
        qop: String = "auth",
        userHash: Boolean = false,
        charset: String? = null
    ) = DigestCredential(
        realm = realm,
        userName = userName,
        digestUri = digestUri,
        nonce = nonce,
        opaque = null,
        nonceCount = nc,
        algorithm = algorithm,
        response = "",
        cnonce = cnonce,
        qop = qop,
        userHash = userHash,
        charset = charset
    )

    private fun buildAuthHeader(
        userName: String,
        realm: String,
        nonce: String,
        uri: String,
        response: String,
        algorithm: String,
        nc: String,
        cnonce: String,
        qop: String = "auth",
        userhash: Boolean? = null
    ): String {
        val userhashPart = if (userhash != null) ", userhash=$userhash" else ""
        return "Digest username=\"$userName\", realm=\"$realm\", " +
            "nonce=\"$nonce\", uri=\"$uri\", " +
            "response=\"$response\", " +
            "algorithm=$algorithm, qop=$qop, nc=$nc, cnonce=\"$cnonce\"$userhashPart"
    }

    private fun ApplicationTestBuilder.setupDigestAuth(
        realm: String = "test",
        algorithms: List<DigestAlgorithm> = listOf(DigestAlgorithm.SHA_256),
        qop: List<DigestQop> = listOf(DigestQop.AUTH),
        users: Map<String, String> = mapOf("user" to "pass"),
        userHashResolver: UserHashResolverFunction? = null,
        strictRfc7616Mode: Boolean = false,
        charset: java.nio.charset.Charset? = null
    ) {
        install(Authentication) {
            digest {
                this.realm = realm
                this.algorithms = algorithms
                this.supportedQop = qop
                if (strictRfc7616Mode) this.strictRfc7616Mode = true
                charset?.let { this.charset = it }
                userHashResolver?.let { resolver -> this.userHashResolver(resolver) }
                digestProvider { userName, providerRealm, algorithm ->
                    users[userName]?.let { password ->
                        computeHA1(userName, providerRealm, password, algorithm)
                    }
                }
            }
        }
        routing {
            authenticate {
                get("/") { call.respondText("OK") }
                get("/resource") { call.respondText("OK") }
                post("/api/data") { call.respondText("OK") }
            }
        }
    }

    @Test
    fun testRFC7616TestVectors() {
        // RFC 7616 Section 3.9.1 test vector
        val realm = "http-auth@example.org"
        val userName = "Mufasa"
        val password = "CircleOfLife"
        val nonce = "dcd98b7102dd2f0e8b11d0f600bfb0c093"
        val cnonce = "0a4f113b"

        // Test SHA-256 HA1
        val ha1Sha256 = digest(DigestAlgorithm.SHA_256, "$userName:$realm:$password")
        assertEquals("4e4b0731c5b9505beb5a6778cae26063644cebafe6969275be09e7bca2b4da6e", hex(ha1Sha256))

        // Test SHA-256 response
        val credentialSha256 = createCredential(realm, userName, "/dir/index.html", nonce, "SHA-256", cnonce)
        assertEquals(
            "1065210cae09a9f712a625aae28b6119cbb2bb852820f94706624b43328c7b0d",
            hex(credentialSha256.expectedDigest(HttpMethod.Get, ha1Sha256))
        )

        // Test SHA-512-256 produces the correct length
        val ha1Sha512 = digest(DigestAlgorithm.SHA_512_256, "$userName:$realm:$password")
        val credentialSha512 = createCredential(realm, userName, "/dir/index.html", nonce, "SHA-512-256", cnonce)
        assertEquals(64, hex(credentialSha512.expectedDigest(HttpMethod.Get, ha1Sha512)).length)

        // Test MD5 legacy compatibility (RFC 2617)
        val realmMd5 = "testrealm@host.com"
        val passwordMd5 = "Circle Of Life"
        val ha1Md5 = digest(DigestAlgorithm.MD5, "$userName:$realmMd5:$passwordMd5", Charsets.ISO_8859_1)
        val credentialMd5 = createCredential(realmMd5, userName, "/dir/index.html", nonce, null, cnonce)
        assertEquals("6629fae49393a05397450978507c4ef1", hex(credentialMd5.expectedDigest(HttpMethod.Get, ha1Md5)))
    }

    @Test
    fun testSessionAlgorithms() {
        val userName = "Mufasa"
        val realm = "http-auth@example.org"
        val password = "CircleOfLife"
        val nonce = "7ypf/xlj9XXwfDPEoM4URrv/xwf94BcCAzFZH4GiTo0v"
        val cnonce = "f2/wE4q74E6zIJEtWaHKaf5wv/H5QzzpXusqGemxURZJ"

        // Test SHA-256-sess
        val baseHa1Sha256 = digest(DigestAlgorithm.SHA_256, "$userName:$realm:$password")
        val sessionHa1Sha256 = digest(DigestAlgorithm.SHA_256, "${hex(baseHa1Sha256)}:$nonce:$cnonce")
        val credentialSha256Sess = createCredential(realm, userName, "/", nonce, "SHA-256-sess", cnonce)
        val ha2Sha256 = digest(DigestAlgorithm.SHA_256, "GET:/")
        val expectedSha256 = digest(
            DigestAlgorithm.SHA_256,
            "${hex(sessionHa1Sha256)}:$nonce:00000001:$cnonce:auth:${hex(ha2Sha256)}"
        )
        assertEquals(hex(expectedSha256), hex(credentialSha256Sess.expectedDigest(HttpMethod.Get, baseHa1Sha256)))

        // Test MD5-sess
        val realmMd5 = "testrealm@host.com"
        val passwordMd5 = "Circle Of Life"
        val nonceMd5 = "dcd98b7102dd2f0e8b11d0f600bfb0c093"
        val cnonceMd5 = "0a4f113b"
        val baseHa1Md5 = digest(DigestAlgorithm.MD5, "$userName:$realmMd5:$passwordMd5", Charsets.ISO_8859_1)
        val sessionHa1Md5 = digest(DigestAlgorithm.MD5, "${hex(baseHa1Md5)}:$nonceMd5:$cnonceMd5", Charsets.ISO_8859_1)
        val credentialMd5Sess = createCredential(realmMd5, userName, "/dir/index.html", nonceMd5, "MD5-sess", cnonceMd5)
        val ha2Md5 = digest(DigestAlgorithm.MD5, "GET:/dir/index.html", Charsets.ISO_8859_1)
        val expectedMd5 = digest(
            DigestAlgorithm.MD5,
            "${hex(sessionHa1Md5)}:$nonceMd5:00000001:$cnonceMd5:auth:${hex(ha2Md5)}",
            Charsets.ISO_8859_1
        )
        assertEquals(hex(expectedMd5), hex(credentialMd5Sess.expectedDigest(HttpMethod.Get, baseHa1Md5)))
    }

    // ==================== QoP Tests ====================

    @Test
    fun testQopAuthInt() {
        val realm = "api@example.org"
        val entityBody = """{"key": "value"}"""
        val algorithm = DigestAlgorithm.SHA_256

        val ha1 = digest(algorithm, "user:$realm:pass")
        val entityBodyHash = algorithm.toDigester().apply { update(entityBody.toByteArray()) }.digest()
        val ha2 = digest(algorithm, "POST:/api/data:${hex(entityBodyHash)}")
        val expectedResponse = digest(algorithm, "${hex(ha1)}:abc123:00000001:xyz789:auth-int:${hex(ha2)}")

        val credential = DigestCredential(
            realm = realm, userName = "user", digestUri = "/api/data", nonce = "abc123",
            opaque = null, nonceCount = "00000001", algorithm = "SHA-256",
            response = hex(expectedResponse), cnonce = "xyz789", qop = "auth-int"
        )

        assertEquals(
            credential.response,
            hex(credential.expectedDigest(HttpMethod.Post, ha1, entityBodyHash))
        )
    }

    @Test
    fun testQopInChallenge() = testApplication {
        setupDigestAuth(qop = listOf(DigestQop.AUTH, DigestQop.AUTH_INT))

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate]!!
        assertTrue(wwwAuth.contains("qop="))
        assertTrue(wwwAuth.contains("auth"))
        assertTrue(wwwAuth.contains("auth-int"))
    }

    // ==================== Userhash Tests ====================

    @Test
    fun testComputeUserHash() {
        val username = "Mufasa"
        val realm = "http-auth@example.org"
        val algorithm = DigestAlgorithm.SHA_256

        val userhash = computeUserHash(username, realm, algorithm)
        val expected = hex(digest(algorithm, "$username:$realm"))

        assertEquals(expected, userhash)
        assertEquals(64, userhash.length)
    }

    @Test
    fun testUserhashAuthentication() = testApplication {
        val users = mapOf("Mufasa" to "CircleOfLife")
        val realm = "http-auth@example.org"

        setupDigestAuth(
            realm = realm,
            users = users,
            userHashResolver = { userhash: String, resolverRealm: String, algorithm: DigestAlgorithm ->
                users.keys.find { computeUserHash(it, resolverRealm, algorithm) == userhash }
            }
        )

        val nonce = "testnonce"
        val cnonce = "testcnonce"
        val userhash = computeUserHash("Mufasa", realm, DigestAlgorithm.SHA_256)
        val ha1 = computeHA1("Mufasa", realm, "CircleOfLife", DigestAlgorithm.SHA_256)
        val credential = createCredential(realm, userhash, "/", nonce, "SHA-256", cnonce, userHash = true)
        val response = hex(credential.expectedDigest(HttpMethod.Get, ha1))

        // Test successful userhash authentication
        val authResponse = client.get("/") {
            header(
                HttpHeaders.Authorization,
                buildAuthHeader(userhash, realm, nonce, "/", response, "SHA-256", "00000001", cnonce, userhash = true)
            )
        }
        assertEquals(HttpStatusCode.OK, authResponse.status)

        // Test unknown user fails
        val fakeUserhash = computeUserHash("bob", realm, DigestAlgorithm.SHA_256)
        val failResponse = client.get("/") {
            header(
                HttpHeaders.Authorization,
                buildAuthHeader(
                    fakeUserhash,
                    realm,
                    "testnonce",
                    "/",
                    "fake",
                    "SHA-256",
                    "00000001",
                    "testcnonce",
                    userhash = true
                )
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, failResponse.status)
    }

    @Test
    fun testUserhashWithoutResolverFails() = testApplication {
        setupDigestAuth()

        val userhash = computeUserHash("user", "test", DigestAlgorithm.SHA_256)
        val response = client.get("/") {
            header(
                HttpHeaders.Authorization,
                buildAuthHeader(
                    userhash,
                    "test",
                    "testnonce",
                    "/",
                    "fake",
                    "SHA-256",
                    "00000001",
                    "testcnonce",
                    userhash = true
                )
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testUserhashAdvertisedInChallenge() = testApplication {
        setupDigestAuth(userHashResolver = { _: String, _: String, _: DigestAlgorithm -> "userhash" })

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.contains("userhash=true"))
    }

    // ==================== Multiple Algorithm Tests ====================

    @Test
    fun testMultipleAlgorithmChallenges() = testApplication {
        setupDigestAuth(algorithms = listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.MD5))

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val challenges = response.headers.getAll(HttpHeaders.WWWAuthenticate)!!
        assertTrue(challenges.any { it.contains("algorithm=SHA-256") })
        assertTrue(challenges.any { it.contains("algorithm=MD5") })
    }

    // ==================== Header Syntax Tests ====================

    @Test
    fun testChallengeHeaderSyntax() = testApplication {
        setupDigestAuth()

        val response = client.get("/")
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate]!!

        assertTrue(wwwAuth.contains("algorithm=SHA-256"), "algorithm should be unquoted")
        assertFalse(wwwAuth.contains("algorithm=\"SHA-256\""), "algorithm should not be quoted")
        assertTrue(wwwAuth.contains("qop=\"auth\""), "qop should be quoted per RFC 7616")
    }

    // ==================== Authentication-Info Header Tests ====================

    @Test
    fun testAuthenticationInfoHeader() = testApplication {
        setupDigestAuth(algorithms = listOf(DigestAlgorithm.MD5))

        val nonce = "testnonce"
        val cnonce = "testcnonce"
        val nc = "00000001"
        val ha1 = computeHA1("user", "test", "pass", DigestAlgorithm.MD5)
        val credential = createCredential("test", "user", "/", nonce, "MD5", cnonce)
        val response = hex(credential.expectedDigest(HttpMethod.Get, ha1))

        val authResponse = client.get("/") {
            header(HttpHeaders.Authorization, buildAuthHeader("user", "test", nonce, "/", response, "MD5", nc, cnonce))
        }

        assertEquals(HttpStatusCode.OK, authResponse.status)
        val authInfo = authResponse.headers["Authentication-Info"]!!
        assertTrue(authInfo.contains("rspauth="))
        assertTrue(authInfo.contains("nextnonce="))
        assertTrue(authInfo.contains("qop=auth"))
        assertTrue(authInfo.contains("nc=$nc"))
        assertTrue(authInfo.contains("cnonce=\"$cnonce\""))

        // Verify rspauth value
        val ha2ForRspauth = hex(digest(DigestAlgorithm.MD5, ":/", Charsets.ISO_8859_1))
        val expectedRspauth =
            hex(digest(DigestAlgorithm.MD5, "${hex(ha1)}:$nonce:$nc:$cnonce:auth:$ha2ForRspauth", Charsets.ISO_8859_1))
        assertTrue(authInfo.contains("rspauth=\"$expectedRspauth\""))
    }

    @Test
    fun testAuthenticationInfoWithSessionAlgorithm() = testApplication {
        setupDigestAuth(algorithms = listOf(DigestAlgorithm.MD5_SESS))

        val nonce = "sessnonce"
        val cnonce = "sesscnonce"
        val nc = "00000001"
        val baseHa1 = computeHA1("user", "test", "pass", DigestAlgorithm.MD5)
        val digester = MessageDigest.getInstance("MD5")

        fun md5(data: String) = digester.apply {
            reset()
            update(data.toByteArray(Charsets.ISO_8859_1))
        }.digest()

        val sessionHa1 = md5("${hex(baseHa1)}:$nonce:$cnonce")
        val ha2 = md5("GET:/")
        val response = hex(md5("${hex(sessionHa1)}:$nonce:$nc:$cnonce:auth:${hex(ha2)}"))

        val authResponse = client.get("/") {
            header(
                HttpHeaders.Authorization,
                buildAuthHeader("user", "test", nonce, "/", response, "MD5-sess", nc, cnonce)
            )
        }

        assertEquals(HttpStatusCode.OK, authResponse.status)
        val authInfo = authResponse.headers["Authentication-Info"]!!
        assertTrue(authInfo.contains("rspauth="))

        val expectedRspauth = hex(md5("${hex(sessionHa1)}:$nonce:$nc:$cnonce:auth:${hex(md5(":/"))}"))
        assertTrue(authInfo.contains("rspauth=\"$expectedRspauth\""))
    }

    // ==================== Charset Tests ====================

    @Test
    fun testCharsetUTF8InChallenge() = testApplication {
        setupDigestAuth(charset = Charsets.UTF_8)

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.contains("charset=UTF-8"))
    }

    @Test
    fun testStrictModeValidation() {
        // MD5 prohibited
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                setupDigestAuth(
                    algorithms = listOf(DigestAlgorithm.MD5),
                    strictRfc7616Mode = true,
                    charset = Charsets.UTF_8
                )
            }
        }

        // MD5-sess prohibited
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                setupDigestAuth(
                    algorithms = listOf(DigestAlgorithm.MD5_SESS),
                    strictRfc7616Mode = true,
                    charset = Charsets.UTF_8
                )
            }
        }

        // UTF-8 required
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                setupDigestAuth(
                    algorithms = listOf(DigestAlgorithm.SHA_256),
                    strictRfc7616Mode = true
                )
            }
        }
    }

    @Test
    fun testStrictModeAllowsSecureAlgorithms() = testApplication {
        setupDigestAuth(
            algorithms = listOf(DigestAlgorithm.SHA_256),
            strictRfc7616Mode = true,
            charset = Charsets.UTF_8
        )

        val response = client.get("/")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val wwwAuth = response.headers[HttpHeaders.WWWAuthenticate]!!
        assertTrue(wwwAuth.contains("algorithm=SHA-256"))
        assertTrue(wwwAuth.contains("charset=UTF-8"))
    }

    @Test
    fun testDigestAlgorithmEnum() {
        // Parsing
        assertEquals(DigestAlgorithm.MD5, DigestAlgorithm.from("MD5"))
        assertEquals(DigestAlgorithm.MD5_SESS, DigestAlgorithm.from("MD5-sess"))
        assertEquals(DigestAlgorithm.SHA_256, DigestAlgorithm.from("SHA-256"))
        assertEquals(DigestAlgorithm.SHA_256_SESS, DigestAlgorithm.from("SHA-256-sess"))
        assertEquals(DigestAlgorithm.SHA_512_256, DigestAlgorithm.from("SHA-512-256"))
        assertEquals(DigestAlgorithm.SHA_512_256_SESS, DigestAlgorithm.from("SHA-512-256-sess"))
        assertNull(DigestAlgorithm.from("unknown"))

        // Properties
        assertTrue(DigestAlgorithm.MD5_SESS.isSession)
        assertTrue(DigestAlgorithm.SHA_256_SESS.isSession)
        assertFalse(DigestAlgorithm.MD5.isSession)
        assertFalse(DigestAlgorithm.SHA_256.isSession)
        assertEquals("MD5", DigestAlgorithm.MD5.hashName)
        assertEquals("MD5", DigestAlgorithm.MD5_SESS.hashName)
        assertEquals("SHA-256", DigestAlgorithm.SHA_256.hashName)
        assertEquals("SHA-512/256", DigestAlgorithm.SHA_512_256.hashName)
    }

    @Test
    fun testDigestQopEnum() {
        assertEquals(DigestQop.AUTH, DigestQop.from("auth"))
        assertEquals(DigestQop.AUTH_INT, DigestQop.from("auth-int"))
        assertNull(DigestQop.from("unknown"))
    }

    @Test
    fun testCredentialComputeHA1() {
        val baseDigest = computeHA1("user", "realm", "pass", DigestAlgorithm.MD5)

        // Non-session: returns base digest unchanged
        val credential = createCredential("realm", "user", "/", "nonce", "MD5", "cnonce")
        assertContentEquals(baseDigest, credential.computeHA1(baseDigest))

        // Session: returns H(baseDigest:nonce:cnonce)
        val credentialSess = createCredential("realm", "user", "/", "nonce", "MD5-sess", "cnonce")
        val digester = MessageDigest.getInstance("MD5")
        digester.update("${hex(baseDigest)}:nonce:cnonce".toByteArray(Charsets.ISO_8859_1))
        assertContentEquals(digester.digest(), credentialSess.computeHA1(baseDigest))
    }

    @Test
    fun testBuildAuthenticationInfoHeader() {
        val ha1 = computeHA1("user", "realm", "pass", DigestAlgorithm.MD5)
        val credential = createCredential("realm", "user", "/path", "nonce", "MD5", "cnonce")
        val authInfo = credential.buildAuthenticationInfoHeader(ha1, "nextnonce")

        assertTrue(authInfo.contains("rspauth=\""))
        assertTrue(authInfo.contains("qop=auth"))
        assertTrue(authInfo.contains("nc=00000001"))
        assertTrue(authInfo.contains("cnonce=\"cnonce\""))
        assertTrue(authInfo.contains("nextnonce=\"nextnonce\""))

        val ha2 = hex(digest(DigestAlgorithm.MD5, ":/path", Charsets.ISO_8859_1))
        val rspauthInput = "${hex(ha1)}:nonce:00000001:cnonce:auth:$ha2"
        val expectedRspauth = hex(digest(DigestAlgorithm.MD5, rspauthInput, Charsets.ISO_8859_1))
        assertTrue(authInfo.contains("rspauth=\"$expectedRspauth\""))
    }

    @Test
    fun testIntegrationSHA256() = testApplication {
        val realm = "http-auth@example.org"
        setupDigestAuth(
            realm = realm,
            algorithms = listOf(DigestAlgorithm.SHA_256, DigestAlgorithm.MD5),
            users = mapOf("Mufasa" to "CircleOfLife")
        )

        val response = client.get("/") {
            header(
                HttpHeaders.Authorization,
                "Digest username=\"Mufasa\", realm=\"$realm\", " +
                    "nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", uri=\"/\", " +
                    "response=\"4ca7fe3a9a741248f4c17e5c36560060f4f36077a7d6a815912661679ddcc67d\", " +
                    "algorithm=SHA-256, qop=auth, nc=00000001, cnonce=\"0a4f113b\""
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}
