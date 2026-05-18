/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.RateLimitReachedException
import com.auth0.jwk.SigningKeyNotFoundException
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class OidcJwkProviderConfigTest {

    @Test
    fun `cache config caches fetched keys`() {
        val keys = OpenIdTestKeys()

        withJwksServer(keys) { jwksUri, fetchCount ->
            val jwkProvider = oidcJwkProvider(jwksUri) {
                jwkCache(maxEntries = 1, duration = 1.hours)
            }

            assertEquals(keys.jwk.id, jwkProvider.get(keys.jwk.id).id)
            assertEquals(keys.jwk.id, jwkProvider.get(keys.jwk.id).id)
            assertEquals(1, fetchCount.get())
        }
    }

    @Test
    fun `disableCache lets repeated key lookups reach the rate limiter`() {
        val keys = OpenIdTestKeys()

        withJwksServer(keys) { jwksUri, fetchCount ->
            val jwkProvider = oidcJwkProvider(jwksUri) {
                disableJwkCache()
                jwkRateLimit(bucketSize = 1, refillDuration = 1.hours)
            }

            assertEquals(keys.jwk.id, jwkProvider.get(keys.jwk.id).id)
            assertFailsWith<RateLimitReachedException> {
                jwkProvider.get(keys.jwk.id)
            }
            assertEquals(1, fetchCount.get())
        }
    }

    @Test
    fun `rateLimit limits repeated unknown-key lookups`() {
        val keys = OpenIdTestKeys()

        withJwksServer(keys) { jwksUri, fetchCount ->
            val jwkProvider = oidcJwkProvider(jwksUri) {
                disableJwkCache()
                jwkRateLimit(bucketSize = 1, refillDuration = 1.hours)
            }

            assertFailsWith<SigningKeyNotFoundException> {
                jwkProvider.get("missing-1")
            }
            assertFailsWith<RateLimitReachedException> {
                jwkProvider.get("missing-2")
            }
            assertEquals(2, fetchCount.get())
        }
    }

    @Test
    fun `disableRateLimit allows repeated unknown-key lookups`() {
        val keys = OpenIdTestKeys()

        withJwksServer(keys) { jwksUri, fetchCount ->
            val jwkProvider = oidcJwkProvider(jwksUri) {
                disableJwkCache()
                disableJwkRateLimit()
            }

            repeat(12) { index ->
                assertFailsWith<SigningKeyNotFoundException> {
                    jwkProvider.get("missing-$index")
                }
            }
            assertEquals(13, fetchCount.get())
        }
    }

    @Test
    fun `jwkBuilder remains the final low-level override`() {
        val keys = OpenIdTestKeys()

        withJwksServer(keys) { jwksUri, fetchCount ->
            val jwkProvider = oidcJwkProvider(jwksUri) {
                jwkCache(maxEntries = 1, duration = 1.hours)
                jwkRateLimit(bucketSize = 1, refillDuration = 1.hours)
                jwkBuilder = {
                    cached(false)
                }
            }

            assertEquals(keys.jwk.id, jwkProvider.get(keys.jwk.id).id)
            assertFailsWith<RateLimitReachedException> {
                jwkProvider.get(keys.jwk.id)
            }
            assertEquals(1, fetchCount.get())
        }
    }

    @Test
    fun `default provider is reused while jwks uri is unchanged`() = testApplication {
        val provider = OidcProvider(
            name = "auth0",
            client = client,
            config = OidcProviderConfig("auth0", OidcPrincipal::class).apply {
                issuer = ISSUER_URL
            },
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

        provider.updateMetadata(
            metadata(jwksUri = "http://127.0.0.1:1/jwks-updated")
        )
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

    private fun withJwksServer(
        keys: OpenIdTestKeys,
        block: suspend (jwksUri: String, fetchCount: AtomicInteger) -> Unit,
    ) = runTest {
        val fetchCount = AtomicInteger()
        val port = ServerSocket(0).use { it.localPort }
        val server = embeddedServer(CIO, port = port) {
            routing {
                get("/jwks") {
                    fetchCount.incrementAndGet()
                    call.respondText(
                        text = keys.jwksJson(),
                        status = HttpStatusCode.OK,
                        contentType = ContentType.Application.Json,
                    )
                }
            }
        }.start(wait = false)

        try {
            block("http://127.0.0.1:$port/jwks", fetchCount)
        } finally {
            server.stopSuspend(gracePeriodMillis = 10, timeoutMillis = 10)
        }
    }

    private fun oidcJwkProvider(
        jwksUri: String,
        configureJwt: OidcJwtConfig.() -> Unit,
    ): JwkProvider {
        val client = HttpClient(MockEngine) {
            engine {
                addHandler { respondOk() }
            }
        }
        val provider = try {
            OidcProvider(
                name = "auth0",
                client = client,
                config = OidcProviderConfig("auth0", OidcPrincipal::class).apply {
                    issuer = ISSUER_URL
                    jwt(configureJwt)
                    validate()
                },
            ).apply {
                updateMetadata(metadata(jwksUri = jwksUri))
            }
        } finally {
            client.close()
        }
        return provider.currentJwkProvider()
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

    private fun OpenIdTestKeys.jwksJson(): String {
        val jwk = this.jwk
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
