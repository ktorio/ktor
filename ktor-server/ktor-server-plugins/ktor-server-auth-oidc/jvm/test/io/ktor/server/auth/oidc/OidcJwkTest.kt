/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.NetworkException
import com.auth0.jwk.RateLimitReachedException
import com.auth0.jwk.SigningKeyNotFoundException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class OidcJwkTest {

    @Test
    fun `cache config caches fetched keys`() = withJwksServer(testRsaKeys) { jwksUri, fetchCount ->
        val jwkProvider = oidcProvider(jwksUri) {
            jwkCache(maxEntries = 1, duration = 1.hours)
        }.currentJwkProvider()

        assertEquals(testRsaKeys.keyId, jwkProvider.get(testRsaKeys.keyId).id)
        assertEquals(testRsaKeys.keyId, jwkProvider.get(testRsaKeys.keyId).id)
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun `jwkBuilder remains the final low-level override`() = withJwksServer(testRsaKeys) { jwksUri, fetchCount ->
        val jwkProvider = oidcProvider(jwksUri) {
            jwkCache(maxEntries = 1, duration = 1.hours)
            jwkRateLimit(bucketSize = 1, refillDuration = 1.hours)
            jwkBuilder = {
                cached(false)
            }
        }.currentJwkProvider()

        assertEquals(testRsaKeys.keyId, jwkProvider.get(testRsaKeys.keyId).id)
        assertFailsWith<RateLimitReachedException> {
            jwkProvider.get(testRsaKeys.keyId)
        }
        assertEquals(1, fetchCount.get())
    }

    @Test
    fun `default provider is reused while jwks uri is unchanged`() = testApplication {
        val provider = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0").apply { issuer = ISSUER_URL }
        )
        val initialMetadata = metadata(jwksUri = "http://127.0.0.1:1/jwks")
        provider.updateMetadata(initialMetadata)
        val initialJwkProvider = provider.currentJwkProvider()

        provider.updateMetadata(
            metadata(
                authorizationEndpoint = "$ISSUER_URL/authorize-updated",
                jwksUri = initialMetadata.jwksUri,
            )
        )
        assertSame(initialJwkProvider, provider.currentJwkProvider())

        val newMetadata = metadata(jwksUri = "http://127.0.0.1:1/jwks-updated")
        provider.updateMetadata(newMetadata)

        assertNotSame(initialJwkProvider, provider.currentJwkProvider())
    }

    @Test
    fun `jwk cache and rate-limit config validates positive values`() {
        assertFailsWith<IllegalArgumentException> {
            OidcJwtConfig().jwkCache(maxEntries = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            OidcJwtConfig().jwkCache(duration = 0.seconds)
        }
        assertFailsWith<IllegalArgumentException> {
            OidcJwtConfig().jwkRateLimit(bucketSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            OidcJwtConfig().jwkRateLimit(refillDuration = 0.seconds)
        }
    }

    @Test
    fun `unknown kid is still rejected as an invalid token`() = withJwksServer(testRsaKeys) { jwksUri, _ ->
        val provider = oidcProvider(jwksUri)
        val token = testOtherRsaKeys.accessToken { subject = "unknown-kid" }

        assertFailsWith<OidcTokenRejectedException> { provider.verifyJwtAccessToken(token) }
    }

    @Test
    fun `unreachable JWKS endpoint reports the signing key as unavailable`() = runTest {
        val jwksUri = "http://127.0.0.1:1/jwks"
        val provider = oidcProvider(jwksUri)
        val token = testRsaKeys.accessToken { subject = "unreachable" }

        val failure = assertFailsWith<OidcSigningKeyUnavailableException> { provider.verifyJwtAccessToken(token) }
        assertIs<NetworkException>(failure.cause)
        // Ktor's default error response echoes the message, so the endpoint stays in the cause and out of it.
        assertFalse(jwksUri in failure.message.orEmpty())
    }

    @Test
    fun `exhausted JWKS rate limit reports the signing key as unavailable`() =
        withJwksServer(testRsaKeys) { jwksUri, fetchCount ->
            // The cache has to be off, or it answers the second lookup before the rate limiter ever sees it.
            val provider = oidcProvider(jwksUri) {
                disableJwkCache()
                jwkRateLimit(bucketSize = 1, refillDuration = 1.hours)
            }
            val token = testRsaKeys.accessToken { subject = "rate-limited" }

            assertEquals("rate-limited", provider.verifyJwtAccessToken(token).claims.subject)

            val failure = assertFailsWith<OidcSigningKeyUnavailableException> { provider.verifyJwtAccessToken(token) }
            assertIs<RateLimitReachedException>(failure.cause)
            assertEquals(1, fetchCount.get())
        }

    @Test
    fun `malformed JWKS document reports the signing key as unavailable`() =
        // A key entry without "kty" is what jwks-rsa reports as a parse failure, carrying a cause.
        withJwksServer(body = """{"keys": [{"kid": "kid-1", "alg": "RS256"}]}""") { jwksUri, _ ->
            val provider = oidcProvider(jwksUri)
            val token = testRsaKeys.accessToken { subject = "malformed-jwks" }

            val failure = assertFailsWith<OidcSigningKeyUnavailableException> { provider.verifyJwtAccessToken(token) }
            assertIs<SigningKeyNotFoundException>(failure.cause)
            assertNotNull(failure.cause?.cause)
        }

    @Test
    fun `bearer authentication answers an unavailable signing key with a server error, not unauthorized`() =
        testApplication {
            installJwtBearer(
                jwkProvider = JwkProvider { throw NetworkException("boom", IOException("refused")) }
            )
            val token = testRsaKeys.accessToken { subject = "outage" }

            val response = client.get("/protected") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun `bearer authentication still responds unauthorized for an unknown kid`() = testApplication {
        installJwtBearer(jwkProvider = jwkProviderWithMultipleKeys(testRsaKeys))
        val token = testOtherRsaKeys.accessToken { subject = "unknown-kid" }

        val response = client.get("/protected") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // The refresh route answers 401 when the refreshed token is rejected, which tells a client its session is
    // gone. A JWKS outage must not be reported that way.
    @Test
    fun `refresh route does not report an unavailable JWKS as unauthorized`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val jwksFailing = AtomicBoolean(false)

        openIdRefreshProvider(idTokensByState) { respondRefreshedIdToken(keys) }
        installSessionTestApp(
            keys = keys,
            configureProvider = {
                jwt {
                    jwkProviderFactory = {
                        JwkProvider { requestedKeyId ->
                            if (jwksFailing.get()) throw NetworkException("boom", IOException("refused"))
                            keys.jwkProvider.get(requestedKeyId)
                        }
                    }
                }
            },
        )

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = (-1).seconds)

        jwksFailing.set(true)
        val refresh = browser.post("/oidc/auth0/refresh") { header(HttpHeaders.Cookie, cookie) }
        assertNotEquals(HttpStatusCode.Unauthorized, refresh.status)
        assertEquals(HttpStatusCode.InternalServerError, refresh.status)
    }

    private fun ApplicationTestBuilder.installJwtBearer(
        metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(ISSUER_URL),
        jwkProvider: JwkProvider? = null,
    ) {
        application {
            val oidc = install(Oidc)
            val provider = oidc.identityProvider("auth0") {
                testIssuer(metadata = metadata)
                jwt { jwkProvider?.let { provider -> jwkProviderFactory = { provider } } }
                bearer { audience = setOf("api") }
            }

            routing {
                authenticateWith(provider.jwtBearer) {
                    get("/protected") { call.respondText(call.principal.claims.subject ?: "ok") }
                }
            }
        }
    }

    private fun oidcProvider(
        jwksUri: String,
        configureJwt: OidcJwtConfig.() -> Unit = {},
    ): OidcProvider {
        val name = "auth0"
        val client = HttpClient(MockEngine) {
            engine { addHandler { respondOk() } }
        }
        val config = OidcProviderConfig(name).apply {
            issuer = ISSUER_URL
            jwt(configureJwt)
            bearer { audience = setOf("api") }
        }
        val provider = OidcProvider(name, client, config)
        val newMetadata = testOpenIdProviderMetadata(ISSUER_URL, jwksUri = jwksUri)
        provider.updateMetadata(newMetadata)
        return provider
    }

    private fun metadata(
        authorizationEndpoint: String = "$ISSUER_URL/authorize",
        jwksUri: String,
    ): OpenIdProviderMetadata = OpenIdProviderMetadata(
        issuer = ISSUER_URL,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = "$ISSUER_URL/token",
        jwksUri = jwksUri,
    )

    private fun withJwksServer(
        keys: OpenIdTestKeys,
        block: suspend (jwksUri: String, fetchCount: AtomicInteger) -> Unit,
    ) = withJwksServer(body = keys.jwksJson(), block = block)

    private fun withJwksServer(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        block: suspend (jwksUri: String, fetchCount: AtomicInteger) -> Unit,
    ) = runTest {
        val fetchCount = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, port = port) {
            routing {
                get("/jwks") {
                    fetchCount.incrementAndGet()
                    call.respondText(body, ContentType.Application.Json, status)
                }
            }
        }.start(wait = false)

        try {
            block("http://127.0.0.1:$port/jwks", fetchCount)
        } finally {
            server.stopSuspend(gracePeriodMillis = 10, timeoutMillis = 10)
        }
    }

    private fun OpenIdTestKeys.jwksJson(): String {
        val jwk = jwkProvider.get(keyId)
        return """
            {
              "keys": [
                {
                  "kid": "${jwk.id}",
                  "kty": "${jwk.type}",
                  "alg": "${jwk.algorithm}",
                  "use": "${jwk.usage}",
                  "n": "${jwk.additionalAttributes["n"]}",
                  "e": "${jwk.additionalAttributes["e"]}"
                }
              ]
            }
        """.trimIndent()
    }
}
