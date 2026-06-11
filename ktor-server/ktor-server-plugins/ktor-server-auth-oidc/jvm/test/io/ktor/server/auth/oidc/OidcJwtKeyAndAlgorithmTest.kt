/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OidcJwtKeyAndAlgorithmTest {

    @Test
    fun `bearer authentication accepts JWT without kid when JWKS key verifies`() = testApplication {
        val keys = testRsaKeys
        installJwtBearer(keys = keys)

        val token = keys.accessToken {
            subject = "missing-kid"
            keyId = null
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("missing-kid", response.bodyAsText())
    }

    @Test
    fun `bearer authentication rejects JWT without kid when JWKS has multiple keys`() = testApplication {
        val keys = testRsaKeys
        val otherKeys = testOtherRsaKeys

        installJwtBearer(jwkProviderFactory = { jwkProviderWithMultipleKeys(keys, otherKeys) })

        val token = keys.accessToken {
            subject = "missing-kid"
            keyId = null
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `bearer authentication rejects JWT when JWK operations do not allow verification`() = testApplication {
        val keys = testRsaKeys

        installJwtBearer(jwkProviderFactory = { jwkProviderWithoutVerifyOperation(keys) })

        val token = keys.accessToken {
            subject = "key-ops"
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `access token algorithms ignore discovery id token allow list by default`() = testApplication {
        val keys = testRsaKeys
        installJwtBearer(
            keys = keys,
            metadata = OpenIdProviderMetadata(
                issuer = ISSUER_URL,
                authorizationEndpoint = "$ISSUER_URL/authorize",
                tokenEndpoint = "$ISSUER_URL/token",
                jwksUri = "$ISSUER_URL/jwks",
                idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_384.testJwaName),
            ),
        )

        val token = keys.accessToken { subject = "alg-user" }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `access token algorithms honor explicit allow list`() = testApplication {
        val keys = testRsaKeys

        installJwtBearer(
            keys = keys,
            configureJwt = { allowedAlgorithms = setOf(SignatureAlgorithm.RSA_SHA_384) },
        )

        val token = keys.accessToken {
            subject = "alg-user"
        }
        val response = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `openId test keys issue RSA and EC access tokens with static metadata`() = testApplication {
        val rsaKeys = testRsaKeys
        val ecKeys = testEcKeys

        application {
            val oidc = openIdConnect { }
            val rsaProvider = oidc.provider("rsa") {
                testIssuer()
                jwt(rsaKeys)
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }
            val ecProvider = oidc.provider("ec") {
                testIssuer("https://ec.example.com")
                jwt(ecKeys)
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(rsaProvider.bearer) {
                    get("/rsa") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
                authenticateWith(ecProvider.bearer) {
                    get("/ec") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val rsaToken = rsaKeys.accessToken {
            subject = "rsa-user"
        }
        val rsa = client.get("/rsa") {
            header(
                HttpHeaders.Authorization,
                "Bearer $rsaToken"
            )
        }
        assertEquals(HttpStatusCode.OK, rsa.status)
        assertEquals("rsa-user", rsa.bodyAsText())

        val ecToken = ecKeys.accessToken {
            issuer = "https://ec.example.com"
            subject = "ec-user"
        }
        val ec = client.get("/ec") {
            header(HttpHeaders.Authorization, "Bearer $ecToken")
        }
        assertEquals(HttpStatusCode.OK, ec.status)
        assertEquals("ec-user", ec.bodyAsText())

        val ecOnRsaToken = ecKeys.accessToken {
            subject = "ec-on-rsa"
        }
        val ecTokenOnRsaProvider = client.get("/rsa") {
            header(HttpHeaders.Authorization, "Bearer $ecOnRsaToken")
        }
        assertEquals(HttpStatusCode.Unauthorized, ecTokenOnRsaProvider.status)
    }

    @Test
    fun `access token builder preserves user info and custom claims`() = testApplication {
        val keys = testRsaKeys

        application {
            val oidc = openIdConnect { }
            val provider = oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                accessToken { audiences = setOf("api") }
                bearer()
            }

            routing {
                authenticateWith(provider.bearer) {
                    get("/claims") {
                        val accessToken = principal as OidcToken.Access
                        val roles = assertNotNull(
                            accessToken.claims.claim("realm_access")
                                ?.jsonObject
                                ?.get("roles")
                                ?.jsonArray
                                ?.map { it.jsonPrimitive.content }
                        )
                        call.respondText(
                            listOf(
                                accessToken.userInfo?.subject,
                                accessToken.userInfo?.email,
                                accessToken.userInfo?.name,
                                roles.joinToString(","),
                            ).joinToString(":")
                        )
                    }
                }
            }
        }

        val token = keys.accessToken {
            subject = "user-1"
            email = "user@example.com"
            name = "Test User"
            claim("realm_access", mapOf("roles" to listOf("admin", "editor")))
        }

        val response = client.get("/claims") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("user-1:user@example.com:Test User:admin,editor", response.bodyAsText())
    }

    @Test
    fun `id token builder supports nonce and access token hash`() = testApplication {
        val keys = testRsaKeys
        val accessToken = "access-token"

        lateinit var provider: OidcProvider<OidcToken>
        application {
            val oidc = openIdConnect { }
            provider = oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                get("/verify") {
                    val principal = provider.buildIdToken(
                        idToken = keys.idToken(subject = "id-user") {
                            audience = "client-id"
                            nonce = "nonce-1"
                            atHash = keys.algorithm.hashAccessToken(accessToken)
                        },
                        accessToken = accessToken,
                        refreshToken = null,
                        expectedAudience = "client-id",
                        expectedNonce = "nonce-1",
                    )
                    call.respondText(principal.userInfo.subject)
                }
            }
        }

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("id-user", response.bodyAsText())
    }

    @Test
    fun `id token algorithms default to discovery metadata`() = testApplication {
        val keys = testRsaKeys
        val metadata = OpenIdProviderMetadata(
            issuer = ISSUER_URL,
            authorizationEndpoint = "$ISSUER_URL/authorize",
            tokenEndpoint = "$ISSUER_URL/token",
            jwksUri = "$ISSUER_URL/jwks",
            idTokenSigningAlgValuesSupported = listOf(SignatureAlgorithm.RSA_SHA_384.testJwaName),
        )

        application {
            val oidc = openIdConnect { }
            val provider = oidc.provider("auth0") {
                testIssuer(metadata = metadata)
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                get("/verify") {
                    val failure = runCatching {
                        provider.buildIdToken(
                            idToken = keys.idToken(subject = "alg-user") {
                                audience = "client-id"
                            },
                            accessToken = "",
                            refreshToken = null,
                            expectedAudience = "client-id",
                        )
                    }.exceptionOrNull()
                    call.respondText(failure?.message.orEmpty())
                }
            }
        }

        val response = client.get("/verify")
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "allowed algorithms")
    }

    private fun ApplicationTestBuilder.installJwtBearer(
        keys: OpenIdTestKeys? = null,
        jwkProviderFactory: ((String) -> com.auth0.jwk.JwkProvider)? = null,
        metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(ISSUER_URL),
        configureJwt: OidcJwtConfig.() -> Unit = {},
    ) {
        application {
            val oidc = openIdConnect { }
            val provider = oidc.provider("auth0") {
                testIssuer(metadata = metadata)
                if (jwkProviderFactory == null && keys != null) {
                    jwt(keys)
                }
                jwt {
                    configureJwt()
                    jwkProviderFactory?.let { this.jwkProviderFactory = it }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
            }

            routing {
                authenticateWith(provider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "ok")
                    }
                }
            }
        }
    }
}
