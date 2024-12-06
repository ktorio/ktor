/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt

import com.auth0.jwk.*
import com.auth0.jwt.*
import com.auth0.jwt.algorithms.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import java.security.*
import java.security.interfaces.*
import java.util.concurrent.*
import kotlin.test.*

class JWTAuthTest {

    @Test
    fun testJwtNoAuth() = testApplication {
        configureServerJwt()

        val response = client.request("/")

        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwtNoAuthCustomChallengeNoToken() = testApplication {
        configureServerJwt {
            challenge { _, _ ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge("custom1", Charsets.UTF_8)))
            }
        }

        val response = client.request("/")

        verifyResponseUnauthorized(response)
        assertEquals("Basic realm=custom1, charset=UTF-8", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun testJwtMultipleNoAuthCustomChallengeNoToken() = testApplication {
        configureServerJwt {
            challenge { _, _ ->
                call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge("custom1", Charsets.UTF_8)))
            }
        }

        val response = client.request("/")

        verifyResponseUnauthorized(response)
        assertEquals("Basic realm=custom1, charset=UTF-8", response.headers[HttpHeaders.WWWAuthenticate])
    }

    @Test
    fun testJwtWithMultipleConfigurations() = testApplication {
        val validated = mutableSetOf<String>()
        var currentPrincipal: (JWTCredential) -> Any? = { null }

        install(Authentication) {
            jwt(name = "first") {
                realm = "realm1"
                verifier(issuer, audience, algorithm)
                validate {
                    validated.add("1")
                    currentPrincipal(it)
                }
                challenge { _, _ ->
                    call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge("custom1", Charsets.UTF_8)))
                }
            }
            jwt(name = "second") {
                realm = "realm2"
                verifier(issuer, audience, algorithm)
                validate {
                    validated.add("2")
                    currentPrincipal(it)
                }
                challenge { _, _ ->
                    call.respond(UnauthorizedResponse(HttpAuthHeader.basicAuthChallenge("custom2", Charsets.UTF_8)))
                }
            }
        }

        routing {
            authenticate("first", "second") {
                get("/") {
                    val principal = call.authentication.principal<JWTPrincipal>()!!
                    call.respondText("Secret info, ${principal.audience}")
                }
            }
        }

        val token = getToken()
        handleRequestWithToken(token).let { response ->
            verifyResponseUnauthorized(response)
            assertEquals(
                "Basic realm=custom1, charset=UTF-8",
                response.headers[HttpHeaders.WWWAuthenticate]
            )
        }
        assertEquals(setOf("1", "2"), validated)

        currentPrincipal = { JWTPrincipal(it.payload) }
        validated.clear()

        handleRequestWithToken(token).let { response ->
            assertEquals(HttpStatusCode.OK, response.status)

            assertEquals(
                "Secret info, [$audience]",
                response.bodyAsText()
            )

            assertNull(response.headers[HttpHeaders.WWWAuthenticate])
        }

        assertEquals(setOf("1"), validated)
    }

    @Test
    fun testJwtSuccess() = testApplication {
        configureServerJwt()

        val token = getToken()

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwtSuccessWithCustomScheme() = testApplication {
        configureServerJwt {
            authSchemes("Bearer", "Token")
        }

        val token = getToken(scheme = "Token")

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwtSuccessWithCustomSchemeWithDifferentCases() = testApplication {
        configureServerJwt {
            authSchemes("Bearer", "tokEN")
        }

        val token = getToken(scheme = "TOKen")

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwtAlgorithmMismatch() = testApplication {
        configureServerJwt()

        val token = JWT.create().withAudience(audience).withIssuer(issuer).sign(Algorithm.HMAC256("false"))
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwtAudienceMismatch() = testApplication {
        configureServerJwt()
        val token = JWT.create().withAudience("wrong").withIssuer(issuer).sign(algorithm)
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwtIssuerMismatch() = testApplication {
        configureServerJwt()
        val token = JWT.create().withAudience(audience).withIssuer("wrong").sign(algorithm)
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkNoAuth() = testApplication {
        configureServerJwk()

        val response = client.request("/")

        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkSuccess() = testApplication {
        configureServerJwk(mock = true)

        val token = getJwkToken()

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwkSuccessNoIssuer() = testApplication {
        configureServerJwkNoIssuer(mock = true)

        val token = getJwkToken()

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwkSuccessWithLeeway() = testApplication {
        configureServerJwtWithLeeway(mock = true)

        val token = getJwkToken()

        val response = handleRequestWithToken(token)

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    @Test
    fun testJwtAuthSchemeMismatch() = testApplication {
        configureServerJwt()
        val token = getToken().removePrefix("Bearer ")
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwtAuthSchemeMismatch2() = testApplication {
        configureServerJwt()
        val token = getToken("Token")
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwtAuthSchemeMistake() = testApplication {
        configureServerJwt()
        val token = getToken().replace("Bearer", "Bearer:")
        val response = handleRequestWithToken(token)
        verifyResponseBadRequest(response)
    }

    @Test
    fun testJwtBlobPatternMismatch() = testApplication {
        configureServerJwt()
        val token = getToken().let {
            val i = it.length - 2
            it.replaceRange(i..i + 1, " ")
        }
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkAuthSchemeMismatch() = testApplication {
        configureServerJwk(mock = true)
        val token = getJwkToken(false)
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkAuthSchemeMistake() = testApplication {
        configureServerJwk(mock = true)
        val token = getJwkToken(true).replace("Bearer", "Bearer:")
        val response = handleRequestWithToken(token)
        verifyResponseBadRequest(response)
    }

    @Test
    fun testJwkBlobPatternMismatch() = testApplication {
        configureServerJwk(mock = true)
        val token = getJwkToken(true).let {
            val i = it.length - 2
            it.replaceRange(i..i + 1, " ")
        }
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkAlgorithmMismatch() = testApplication {
        configureServerJwk(mock = true)
        val token = JWT.create().withAudience(audience).withIssuer(issuer).sign(Algorithm.HMAC256("false"))
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkAudienceMismatch() = testApplication {
        configureServerJwk(mock = true)
        val token = JWT.create().withAudience("wrong").withIssuer(issuer).sign(algorithm)
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkIssuerMismatch() = testApplication {
        configureServerJwk(mock = true)
        val token = JWT.create().withAudience(audience).withIssuer("wrong").sign(algorithm)
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkKidMismatch() = testApplication {
        configureServerJwk(mock = true)

        val token = "Bearer " + JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withKeyId("wrong")
            .sign(jwkAlgorithm)

        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkInvalidToken() = testApplication {
        configureServerJwk(mock = true)
        val token = "Bearer wrong"
        val response = handleRequestWithToken(token)
        verifyResponseUnauthorized(response)
    }

    @Test
    fun testJwkInvalidTokenCustomChallenge() = testApplication {
        configureServerJwk(mock = true, challenge = true)
        val token = "Bearer wrong"
        val response = handleRequestWithToken(token)
        verifyResponseForbidden(response)
    }

    @Test
    fun verifyWithMock() {
        val token = getJwkToken(prefix = false)
        val provider = getJwkProviderMock()
        val kid = JWT.decode(token).keyId
        val jwk = provider.get(kid)
        val algorithm = jwk.makeAlgorithm()
        val verifier = JWT.require(algorithm).withIssuer(issuer).build()
        verifier.verify(token)
    }

    @Test
    fun verifyNullAlgorithmWithMock() {
        val token = getJwkToken(prefix = false)
        val provider = getJwkProviderNullAlgorithmMock()
        val kid = JWT.decode(token).keyId
        val jwk = provider.get(kid)
        val algorithm = jwk.makeAlgorithm()
        val verifier = JWT.require(algorithm).withIssuer(issuer).build()
        verifier.verify(token)
    }

    @Test
    fun authHeaderFromCookie(): Unit = testApplication {
        configureServer {
            jwt {
                this@jwt.realm = this@JWTAuthTest.realm
                authHeader { call ->
                    call.request.cookies["JWT"]?.let { parseAuthorizationHeader(it) }
                }
                verifier(issuer, audience, algorithm)
                validate { jwt ->
                    JWTPrincipal(jwt.payload)
                }
            }
        }

        val token = getToken()

        val response = client.request("/") {
            header(HttpHeaders.Cookie, "JWT=${token.encodeURLParameter()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().isNotEmpty())
    }

    private suspend fun verifyResponseUnauthorized(response: HttpResponse) {
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().isEmpty())
    }

    private suspend fun verifyResponseBadRequest(response: HttpResponse) {
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().isEmpty())
    }

    private suspend fun verifyResponseForbidden(response: HttpResponse) {
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertTrue(response.bodyAsText().isEmpty())
    }

    private suspend fun ApplicationTestBuilder.handleRequestWithToken(token: String): HttpResponse {
        return client.request("/") {
            header(HttpHeaders.Authorization, token)
        }
    }

    private fun ApplicationTestBuilder.configureServerJwk(
        mock: Boolean = false,
        challenge: Boolean = false
    ) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            if (mock) {
                verifier(getJwkProviderMock())
            } else {
                verifier(issuer)
            }
            validate { credential ->
                when {
                    credential.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }
            if (challenge) {
                challenge { defaultScheme, realm ->
                    call.respond(
                        ForbiddenResponse(
                            HttpAuthHeader.Parameterized(
                                defaultScheme,
                                mapOf(HttpAuthHeader.Parameters.Realm to realm)
                            )
                        )
                    )
                }
            }
        }
    }

    private fun ApplicationTestBuilder.configureServerJwkNoIssuer(mock: Boolean = false) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            if (mock) {
                verifier(getJwkProviderMock())
            } else {
                verifier(issuer)
            }
            verifier(if (mock) getJwkProviderMock() else makeJwkProvider())
            validate { credential ->
                when {
                    credential.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }
        }
    }

    private fun ApplicationTestBuilder.configureServerJwtWithLeeway(mock: Boolean = false) = configureServer {
        jwt {
            this@jwt.realm = this@JWTAuthTest.realm
            if (mock) {
                verifier(getJwkProviderMock()) {
                    acceptLeeway(5)
                }
            } else {
                verifier(issuer) {
                    acceptLeeway(5)
                }
            }
            validate { credential ->
                when {
                    credential.audience.contains(audience) -> JWTPrincipal(credential.payload)
                    else -> null
                }
            }
        }
    }

    private fun ApplicationTestBuilder.configureServerJwt(extra: JWTAuthenticationProvider.Config.() -> Unit = {}) =
        configureServer {
            jwt {
                this@jwt.realm = this@JWTAuthTest.realm
                verifier(issuer, audience, algorithm)
                validate { credential ->
                    when {
                        credential.audience.contains(audience) -> JWTPrincipal(credential.payload)
                        else -> null
                    }
                }
                extra()
            }
        }

    private fun ApplicationTestBuilder.configureServer(authBlock: (AuthenticationConfig.() -> Unit)) {
        install(Authentication) {
            authBlock(this)
        }
        routing {
            authenticate {
                get("/") {
                    val principal = call.authentication.principal<JWTPrincipal>()!!
                    principal.payload
                    call.respondText("Secret info")
                }
            }
        }
    }

    private val algorithm = Algorithm.HMAC256("secret")
    private val keyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048, SecureRandom())
    }.generateKeyPair()
    private val jwkAlgorithm = Algorithm.RSA256(keyPair.public as RSAPublicKey, keyPair.private as RSAPrivateKey)
    private val issuer = "https://jwt-provider-domain/"
    private val audience = "jwt-audience"
    private val realm = "ktor jwt auth test"

    private fun makeJwkProvider(): JwkProvider = JwkProviderBuilder(issuer)
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    private val kid = "NkJCQzIyQzRBMEU4NjhGNUU4MzU4RkY0M0ZDQzkwOUQ0Q0VGNUMwQg"

    private fun getJwkProviderNullAlgorithmMock(): JwkProvider {
        val jwk = mockk<Jwk> {
            every { algorithm } returns null
            every { publicKey } returns keyPair.public
        }
        return mockk {
            every { this@mockk.get(kid) } returns jwk
        }
    }

    private fun getJwkProviderMock(): JwkProvider {
        val jwk = mockk<Jwk> {
            every { algorithm } returns jwkAlgorithm.name
            every { publicKey } returns keyPair.public
        }
        return mockk {
            every { this@mockk.get(kid) } returns jwk
            every { this@mockk.get("wrong") } throws SigningKeyNotFoundException("Key not found", null)
        }
    }

    private fun getJwkToken(prefix: Boolean = true): String = (if (prefix) "Bearer " else "") + JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withKeyId(kid)
        .sign(jwkAlgorithm)

    private fun getToken(scheme: String = "Bearer"): String = "$scheme " + JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .sign(algorithm)
}
