/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.oidc.utils.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class OidcProviderOperationsTest {

    @Test
    fun `refreshToken returns raw fields and verified principal when id token is present`() = runTest {
        val keys = testRsaKeys
        refreshTokenClient(keys).use { client ->
            val provider = operationsProvider(client) {
                testIssuer(metadata = browserFlowMetadata())
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    resourceIndicators = listOf("https://api.example.com")
                }
            }

            val result = provider.refreshToken("refresh-token-1")
            val principal = assertNotNull(result.idToken)
            assertEquals("access-token-2", result.accessToken)
            assertEquals("refresh-token-2", result.refreshToken)
            assertEquals(3600.seconds, result.expiresIn)
            assertEquals("Bearer", result.tokenType)
            assertEquals("openid profile", result.scope)
            assertEquals("refreshed-user", principal.userInfo.subject)
            assertEquals(result.refreshToken, principal.refreshToken)

            val accessOnly = provider.refreshToken("access-only-refresh-token")
            assertEquals("access-token-only", accessOnly.accessToken)
            assertNull(accessOnly.refreshToken)
            assertNull(accessOnly.idToken)

            val notRotated = provider.refreshToken("refresh-token-not-rotated")
            val notRotatedPrincipal = assertNotNull(notRotated.idToken)
            assertNull(notRotated.refreshToken)
            assertEquals("refresh-token-not-rotated", notRotatedPrincipal.refreshToken)
            assertEquals("non-rotated-user", notRotatedPrincipal.userInfo.subject)

            val failure = assertFailsWith<OidcTokenRejectedException> {
                provider.refreshToken("refresh-token-dpop")
            }
            assertContains(failure.message.orEmpty(), "token_type")
        }
    }

    @Test
    fun `buildIdToken validates at hash when present`() = runTest {
        val accessToken = "access-token"
        val keysByAlgorithm = testRsaKeysByAlgorithm

        unusedClient().use { client ->
            val provider = operationsProvider(client) {
                testIssuer()
                jwt {
                    jwkProviderFactory = { jwkProviderWithMultipleKeys(*keysByAlgorithm.values.toTypedArray()) }
                }
            }

            provider.withCapturedState {
                keysByAlgorithm.forEach { (_, keys) ->
                    val principal = provider.buildIdToken(
                        idToken = keys.idToken(subject = "hash-user") {
                            audience = "client-id"
                            atHash = keys.algorithm.hashAccessToken(accessToken)
                        },
                        accessToken = accessToken,
                        refreshToken = null,
                        expectedAudience = "client-id",
                    )
                    assertEquals("hash-user", principal.userInfo.subject)
                }

                val keys = keysByAlgorithm.getValue(SignatureAlgorithm.RSA_SHA_256)
                val failure = assertFailsWith<OidcTokenRejectedException> {
                    provider.buildIdToken(
                        idToken = keys.idToken(subject = "hash-user") {
                            audience = "client-id"
                            atHash = "invalid"
                        },
                        accessToken = accessToken,
                        refreshToken = null,
                        expectedAudience = "client-id",
                    )
                }
                assertContains(failure.message.orEmpty(), "at_hash")
            }
        }
    }

    @Test
    fun `buildLogoutUrl includes id token hint client id and redirect`() = unusedClient().use { client ->
        val provider = operationsProvider(client) {
            testIssuer(metadata = browserFlowMetadata())
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
            }
        }

        val url = Url(
            provider.buildLogoutUrl(
                idTokenHint = "id-token-hint",
                postLogoutRedirectUri = "https://app.example.com/signed-out",
            )
        )
        assertEquals("/logout", url.encodedPath)
        assertEquals("id-token-hint", url.parameters["id_token_hint"])
        assertEquals("https://app.example.com/signed-out", url.parameters["post_logout_redirect_uri"])
        assertEquals("client-id", url.parameters["client_id"])
    }

    @Test
    fun `buildLogoutUrl fails when provider has no logout endpoint`() = unusedClient().use { client ->
        val provider = operationsProvider(client) {
            testIssuer(metadata = openIdProviderMetadata)
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
            }
        }

        val failure = assertFailsWith<IllegalArgumentException> {
            provider.buildLogoutUrl("id-token-hint", null)
        }
        assertContains(assertNotNull(failure.message), "RP-Initiated logout is not supported")
    }

    private fun operationsProvider(
        client: HttpClient,
        configure: OidcProviderConfig.() -> Unit,
    ): OidcProvider {
        val config = OidcProviderConfig("auth0").apply {
            configure()
            validate()
        }
        return OidcProvider(
            name = "auth0",
            client = client,
            config = config,
        ).also { provider ->
            provider.updateMetadata(checkNotNull(config.metadata))
        }
    }

    private fun unusedClient(): HttpClient = HttpClient(MockEngine) {
        engine {
            addHandler { error("HTTP is not used in this test") }
        }
    }

    private fun refreshTokenClient(keys: OpenIdTestKeys): HttpClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json(discoveryJson)
        }
        engine {
            addHandler { request ->
                val parameters = parseQueryString(request.body.toByteArray().decodeToString())
                assertEquals("refresh_token", parameters["grant_type"])
                assertEquals("client-id", parameters["client_id"])
                assertEquals("client-secret", parameters["client_secret"])
                assertEquals(listOf("https://api.example.com"), parameters.getAll("resource"))

                val response = when (parameters["refresh_token"]) {
                    "refresh-token-1" -> TokenRefreshResponse(
                        accessToken = "access-token-2",
                        tokenType = "Bearer",
                        expiresIn = 3600,
                        refreshToken = "refresh-token-2",
                        idToken = keys.idToken(subject = "refreshed-user") {
                            audience = "client-id"
                        },
                        scope = "openid profile",
                    )

                    "access-only-refresh-token" -> TokenRefreshResponse(
                        accessToken = "access-token-only",
                        tokenType = "Bearer",
                    )

                    "refresh-token-not-rotated" -> TokenRefreshResponse(
                        accessToken = "access-token-not-rotated",
                        tokenType = "Bearer",
                        idToken = keys.idToken(subject = "non-rotated-user") {
                            audience = "client-id"
                        },
                    )

                    "refresh-token-dpop" -> TokenRefreshResponse(
                        accessToken = "access-token-bad-type",
                        tokenType = "DPoP",
                        idToken = keys.idToken(subject = "bad-token-type-user") {
                            audience = "client-id"
                        },
                    )

                    else -> return@addHandler respond(
                        content = "",
                        status = HttpStatusCode.BadRequest,
                    )
                }
                respond(
                    content = discoveryJson.encodeToString(response),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
    }
}
