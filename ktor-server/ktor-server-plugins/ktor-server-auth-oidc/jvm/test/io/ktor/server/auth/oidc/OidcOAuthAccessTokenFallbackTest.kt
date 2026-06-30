/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.ZERO

class OidcOAuthAccessTokenFallbackTest {

    @Test
    fun `oauth callback without id token falls back to jwt access token`() = testApplication {
        val keys = testRsaKeys
        openIdProviderWithoutIdToken(keys, tokenType = "DPoP")
        installAccessTokenFallbackProvider(keys) { principal ->
            val accessToken = principal as OidcToken.Access
            call.respondText("signed in ${accessToken.userInfo?.subject}")
        }

        assertOAuthCallback(HttpStatusCode.OK, "signed in access-user")
    }

    @Test
    fun `oauth callback without id token fails when access token policy is absent`() = testApplication {
        openIdProviderWithoutIdToken(testRsaKeys)
        installAccessTokenFallbackProvider(keys = null) {
            call.respondText("unexpected")
        }

        assertOAuthCallback(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `oauth callback without id token falls back to opaque introspection`() = testApplication {
        openIdProviderWithOpaqueAccessToken()
        installOpaqueFallbackProvider()

        assertOAuthCallback(HttpStatusCode.OK, "signed in opaque-user")
    }

    private fun ApplicationTestBuilder.installAccessTokenFallbackProvider(
        keys: OpenIdTestKeys?,
        onSuccess: suspend RoutingContext.(OidcToken) -> Unit,
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                keys?.let { testKeys ->
                    jwt(testKeys)
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

    private fun ApplicationTestBuilder.installOpaqueFallbackProvider() {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                accessToken {
                    audiences = setOf("api")
                    opaqueToken = OpaqueTokenStrategy.Introspect(
                        endpoint = "$ISSUER_URL/introspect",
                        clientId = "resource-server",
                        clientSecret = "secret",
                    )
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onSuccess { principal ->
                        val opaque = principal as OidcToken.Opaque
                        call.respondText("signed in ${opaque.introspection.subject}")
                    }
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
                    post("/token") {
                        val parameters = call.receiveParameters()
                        assertAuthorizationCodeRequest(parameters)
                        call.respondText(
                            listOf(
                                "access_token" to keys.accessToken {
                                    subject = "access-user"
                                },
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

    private fun TestApplicationBuilder.openIdProviderWithOpaqueAccessToken() {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    post("/token") {
                        assertAuthorizationCodeRequest(call.receiveParameters())
                        call.respondText(
                            listOf(
                                "access_token" to "opaque-login-token",
                                "token_type" to "Bearer",
                                "expires_in" to "3600",
                            ).formUrlEncode(),
                            ContentType.Application.FormUrlEncoded,
                        )
                    }
                    post("/introspect") {
                        val parameters = call.receiveParameters()
                        assertEquals(parameters["token"], "opaque-login-token")
                        assertEquals("access_token", parameters["token_type_hint"])
                        assertNull(parameters["client_id"])
                        assertNull(parameters["client_secret"])
                        assertEquals(
                            "Basic ${Base64.getEncoder().encodeToString("resource-server:secret".toByteArray())}",
                            call.request.headers[HttpHeaders.Authorization],
                        )
                        call.respondText(
                            """{"active":true,"sub":"opaque-user","aud":["api"],"scope":"openid"}""",
                            ContentType.Application.Json,
                        )
                    }
                }
            }
        }
    }
}
