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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.*

class OidcProviderOperationsTest {

    @Test
    fun `refreshToken returns raw fields and verified principal when id token is present`() = testApplication {
        val keys = testRsaKeys

        openIdProvider(keys)

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
            }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer(metadata = browserFlowMetadata())
                jwt(keys)
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    resourceIndicators = listOf("https://api.example.com")
                }
            }
            routing {
                get("/refresh") {
                    val result = oidcProvider.refreshToken("refresh-token-1")
                    val principal = assertNotNull(result.idToken)
                    call.respondText(
                        listOf(
                            result.accessToken,
                            result.refreshToken,
                            principal.value,
                            result.expiresIn?.inWholeSeconds.toString(),
                            result.tokenType,
                            result.scope,
                            principal.userInfo.subject,
                        ).joinToString(":")
                    )
                }
                get("/refresh/access-only") {
                    val result = oidcProvider.refreshToken("access-only-refresh-token")
                    call.respondText("${result.accessToken}:${result.refreshToken}:${result.idToken}")
                }
                get("/refresh/not-rotated") {
                    val result = oidcProvider.refreshToken("refresh-token-not-rotated")
                    val principal = assertNotNull(result.idToken)
                    call.respondText("${result.refreshToken}:${principal.refreshToken}:${principal.userInfo.subject}")
                }
                get("/refresh/bad-token-type") {
                    val failure = assertFailsWith<OidcTokenRejectedException> {
                        oidcProvider.refreshToken("refresh-token-dpop")
                    }
                    call.respondText(failure.message.orEmpty())
                }
                get("/logout-url") {
                    val url = oidcProvider.buildLogoutUrl(
                        idTokenHint = "id-token-hint",
                        postLogoutRedirectUri = "https://app.example.com/signed-out",
                    )
                    call.respondText(url)
                }
                authenticateWith(oidcProvider.bearer) {
                    get("/context-refresh") {
                        val result = oidcProvider.refreshToken("refresh-token-1")
                        val principal = assertNotNull(result.idToken)
                        call.respondText("${result.accessToken}:${principal.userInfo.subject}")
                    }
                    get("/context-logout-url") {
                        val url = oidcProvider.buildLogoutUrl(
                            idTokenHint = "id-token-hint",
                            postLogoutRedirectUri = "https://app.example.com/signed-out",
                        )
                        call.respondText(url)
                    }
                }
            }
        }

        val routeToken = keys.accessToken {
            subject = "api-user"
        }
        val refresh = client.get("/refresh")
        assertEquals(HttpStatusCode.OK, refresh.status)
        val parts = refresh.bodyAsText().split(":")
        assertEquals("access-token-2", parts[0])
        assertEquals("refresh-token-2", parts[1])
        assertEquals("3600", parts[3])
        assertEquals("Bearer", parts[4])
        assertEquals("openid profile", parts[5])
        assertEquals("refreshed-user", parts[6])

        val accessOnlyRefresh = client.get("/refresh/access-only")
        assertEquals(HttpStatusCode.OK, accessOnlyRefresh.status)
        assertEquals(accessOnlyRefresh.bodyAsText(), "access-token-only:null:null")

        val notRotatedRefresh = client.get("/refresh/not-rotated")
        assertEquals(HttpStatusCode.OK, notRotatedRefresh.status)
        assertEquals("null:refresh-token-not-rotated:non-rotated-user", notRotatedRefresh.bodyAsText())

        val badTokenType = client.get("/refresh/bad-token-type")
        assertEquals(HttpStatusCode.OK, badTokenType.status)
        assertContains(badTokenType.bodyAsText(), "token_type")

        val logoutUrl = Url(client.get("/logout-url").bodyAsText())
        assertEquals("/logout", logoutUrl.encodedPath)
        assertEquals(logoutUrl.parameters["id_token_hint"], "id-token-hint")
        assertEquals("https://app.example.com/signed-out", logoutUrl.parameters["post_logout_redirect_uri"])
        assertEquals("client-id", logoutUrl.parameters["client_id"])

        val contextRefresh = client.get("/context-refresh") {
            header(HttpHeaders.Authorization, "Bearer $routeToken")
        }
        assertEquals(HttpStatusCode.OK, contextRefresh.status)
        assertEquals(contextRefresh.bodyAsText(), "access-token-2:refreshed-user")

        val contextLogoutUrl = Url(
            client.get("/context-logout-url") {
                header(HttpHeaders.Authorization, "Bearer $routeToken")
            }.bodyAsText()
        )
        assertEquals("/logout", contextLogoutUrl.encodedPath)
        assertEquals(contextLogoutUrl.parameters["id_token_hint"], "id-token-hint")
        assertEquals("https://app.example.com/signed-out", contextLogoutUrl.parameters["post_logout_redirect_uri"])
        assertEquals("client-id", contextLogoutUrl.parameters["client_id"])
    }

    @Test
    fun `buildVerifiedPrincipal validates at hash when present`() = testApplication {
        val accessToken = "access-token"
        val keysByAlgorithm = testRsaKeysByAlgorithm
        val algorithms = keysByAlgorithm.keys

        application {
            val oidc = openIdConnect { }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer()
                jwt {
                    jwkProviderFactory = { jwkProviderWithMultipleKeys(*keysByAlgorithm.values.toTypedArray()) }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }
            routing {
                algorithms.forEach { algorithm ->
                    val algorithmName = algorithm.testJwaName
                    get("/at-hash/valid/$algorithmName") {
                        val keys = keysByAlgorithm.getValue(algorithm)
                        val idToken = keys.idToken(subject = "hash-user") {
                            audience = "client-id"
                            atHash = keys.algorithm.hashAccessToken(accessToken)
                        }
                        val principal = oidcProvider.buildIdToken(
                            idToken = idToken,
                            accessToken = accessToken,
                            refreshToken = null,
                            expectedAudience = "client-id",
                        )
                        call.respondText(principal.userInfo.subject)
                    }
                }
                get("/at-hash/invalid") {
                    val keys = keysByAlgorithm.getValue(SignatureAlgorithm.RSA_SHA_256)
                    val failure = assertFailsWith<OidcTokenRejectedException> {
                        oidcProvider.buildIdToken(
                            idToken = keys.idToken(subject = "hash-user") {
                                audience = "client-id"
                                atHash = "invalid"
                            },
                            accessToken = accessToken,
                            refreshToken = null,
                            expectedAudience = "client-id",
                        )
                    }
                    call.respondText(failure.message.orEmpty())
                }
            }
        }

        algorithms.forEach { algorithm ->
            val valid = client.get("/at-hash/valid/${algorithm.testJwaName}")
            assertEquals(HttpStatusCode.OK, valid.status)
            assertEquals("hash-user", valid.bodyAsText())
        }

        val invalid = client.get("/at-hash/invalid")
        assertEquals(HttpStatusCode.OK, invalid.status)
        assertContains(invalid.bodyAsText(), "at_hash")
    }

    @Test
    fun `buildLogoutUrl returns null when provider has no logout endpoint`() = testApplication {
        application {
            val oidc = openIdConnect { }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer(metadata = openIdProviderMetadata)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }
            routing {
                get("/logout-url") {
                    val failure = assertFailsWith<IllegalArgumentException> {
                        oidcProvider.buildLogoutUrl("id-token-hint", null)
                    }
                    call.respondText(failure.message.orEmpty())
                }
            }
        }

        assertContains(client.get("/logout-url").bodyAsText(), "endSessionEndpoint")
    }

    private fun TestApplicationBuilder.openIdProvider(keys: OpenIdTestKeys) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    post("/token") {
                        val parameters = call.receiveParameters()
                        assertEquals("refresh_token", parameters["grant_type"])
                        assertEquals("client-id", parameters["client_id"])
                        assertEquals("client-secret", parameters["client_secret"])
                        assertEquals(listOf("https://api.example.com"), parameters.getAll("resource"))

                        when (parameters["refresh_token"]) {
                            "refresh-token-1" -> {
                                val idToken = keys.idToken(subject = "refreshed-user") {
                                    audience = "client-id"
                                }
                                call.respondText(
                                    openIdTestJson.encodeToString(
                                        TokenRefreshResponse(
                                            accessToken = "access-token-2",
                                            tokenType = "Bearer",
                                            expiresIn = 3600,
                                            refreshToken = "refresh-token-2",
                                            idToken = idToken,
                                            scope = "openid profile",
                                        )
                                    ),
                                    ContentType.Application.Json,
                                )
                            }

                            "access-only-refresh-token" -> {
                                call.respondText(
                                    openIdTestJson.encodeToString(
                                        TokenRefreshResponse(
                                            accessToken = "access-token-only",
                                            tokenType = "Bearer",
                                        )
                                    ),
                                    ContentType.Application.Json,
                                )
                            }

                            "refresh-token-not-rotated" -> {
                                val idToken = keys.idToken(subject = "non-rotated-user") {
                                    audience = "client-id"
                                }
                                call.respondText(
                                    openIdTestJson.encodeToString(
                                        TokenRefreshResponse(
                                            accessToken = "access-token-not-rotated",
                                            tokenType = "Bearer",
                                            idToken = idToken,
                                        )
                                    ),
                                    ContentType.Application.Json,
                                )
                            }

                            "refresh-token-dpop" -> {
                                val idToken = keys.idToken(subject = "bad-token-type-user") {
                                    audience = "client-id"
                                }
                                call.respondText(
                                    openIdTestJson.encodeToString(
                                        TokenRefreshResponse(
                                            accessToken = "access-token-bad-type",
                                            tokenType = "DPoP",
                                            idToken = idToken,
                                        )
                                    ),
                                    ContentType.Application.Json,
                                )
                            }

                            else -> call.respond(HttpStatusCode.BadRequest)
                        }
                    }
                }
            }
        }
    }
}
