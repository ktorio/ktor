/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.ZERO

class OidcOAuthAccessTokenFallbackTest {

    @Test
    fun `oauth callback without id token falls back to jwt access token`() = testApplication {
        val keys = OpenIdTestKeys()
        openIdProviderWithoutIdToken(keys, tokenType = "DPoP")
        installAccessTokenFallbackProvider(keys) { principal ->
            val accessToken = principal as OidcPrincipal.AccessToken
            call.respondText("signed in ${accessToken.userInfo?.subject}")
        }

        assertOAuthCallback(HttpStatusCode.OK, "signed in access-user")
    }

    @Test
    fun `oauth callback without id token fails when access token policy is absent`() = testApplication {
        openIdProviderWithoutIdToken(OpenIdTestKeys())
        installAccessTokenFallbackProvider(keys = null) {
            call.respondText("unexpected")
        }

        assertOAuthCallback(HttpStatusCode.Unauthorized)
    }

    private fun ApplicationTestBuilder.installAccessTokenFallbackProvider(
        keys: OpenIdTestKeys?,
        onSuccess: suspend RoutingContext.(OidcPrincipal) -> Unit,
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                keys?.let { testKeys ->
                    jwt {
                        jwkProviderFactory = { testKeys.jwkProvider }
                    }
                    accessToken {
                        audiences = setOf("api")
                    }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    this.onSuccess(onSuccess)
                }
            }
        }
    }

    private suspend fun ApplicationTestBuilder.assertOAuthCallback(
        expectedStatus: HttpStatusCode,
        expectedBody: String? = null,
    ) {
        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin()
        val callback = browser.completeOidcCallback(login)
        assertEquals(expectedStatus, callback.status)
        expectedBody?.let { assertEquals(it, callback.bodyAsText()) }
    }

    private fun TestApplicationBuilder.openIdProviderWithoutIdToken(
        keys: OpenIdTestKeys,
        tokenType: String = "Bearer",
    ) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    openIdDiscoveryEndpoint()
                    post("/token") {
                        val parameters = call.receiveParameters()
                        assertAuthorizationCodeRequest(parameters)
                        call.respondText(
                            listOf(
                                "access_token" to keys.token(audience = "api", subject = "access-user"),
                                "token_type" to tokenType,
                                "expires_in" to "3600",
                            ).formUrlEncode(),
                            ContentType.Application.FormUrlEncoded,
                        )
                    }
                }
            }
        }
    }
}
