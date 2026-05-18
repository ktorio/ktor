/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO

class OidcJwtKeyAndAlgorithmTest {

    @Test
    fun `bearer authentication accepts JWT without kid when JWKS key verifies`() = testApplication {
        val keys = OpenIdTestKeys()
        openIdProvider()
        installJwtBearer(keys = keys)

        val response = client.get("/protected") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${keys.token(audience = "api", subject = "missing-kid", keyId = null)}"
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("missing-kid", response.bodyAsText())
    }

    @Test
    fun `bearer authentication rejects JWT without kid when JWKS has multiple keys`() = testApplication {
        val keys = OpenIdTestKeys("kid-1")
        val otherKeys = OpenIdTestKeys("kid-2")

        openIdProvider()
        installJwtBearer(jwkProviderFactory = { jwkProviderWithMultipleKeys(keys, otherKeys) })

        val response = client.get("/protected") {
            header(
                HttpHeaders.Authorization,
                "Bearer ${keys.token(audience = "api", subject = "missing-kid", keyId = null)}"
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `bearer authentication rejects JWT when JWK operations do not allow verification`() = testApplication {
        val keys = OpenIdTestKeys()

        openIdProvider()
        installJwtBearer(jwkProviderFactory = { jwkProviderWithoutVerifyOperation(keys) })

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.token(audience = "api", subject = "key-ops")}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `access token algorithms ignore discovery id token allow list by default`() = testApplication {
        val keys = OpenIdTestKeys()
        openIdProvider(
            OpenIdProviderMetadata(
                issuer = ISSUER_URL,
                authorizationEndpoint = "$ISSUER_URL/authorize",
                tokenEndpoint = "$ISSUER_URL/token",
                jwksUri = "$ISSUER_URL/jwks",
                idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_384.testJwaName),
            )
        )
        installJwtBearer(keys = keys)

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.token(audience = "api", subject = "alg-user")}")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `access token algorithms honor explicit allow list`() = testApplication {
        val keys = OpenIdTestKeys()

        openIdProvider()
        installJwtBearer(
            keys = keys,
            configureJwt = { allowedAlgorithms = setOf(SignatureAlgorithm.RSA_SHA_384.testJwaName) },
        )

        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer ${keys.token(audience = "api", subject = "alg-user")}")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    private fun ApplicationTestBuilder.installJwtBearer(
        keys: OpenIdTestKeys? = null,
        jwkProviderFactory: ((String) -> com.auth0.jwk.JwkProvider)? = null,
        configureJwt: OidcJwtConfig.() -> Unit = {},
    ) {
        val openIdClient = client
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val provider = oidc.provider("auth0") {
                issuer = ISSUER_URL
                jwt {
                    configureJwt()
                    this.jwkProviderFactory = jwkProviderFactory ?: { checkNotNull(keys).jwkProvider }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(provider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcPrincipal.AccessToken
                        call.respondText(accessToken.userInfo?.subject ?: "ok")
                    }
                }
            }
        }
    }
}
