/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, ExperimentalTime::class)

package io.ktor.server.auth.oidc.utils

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.install
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.ExperimentalTime

internal val openIdTestJson = Json { ignoreUnknownKeys = true }

internal fun TestApplicationBuilder.openIdRefreshProvider(
    idTokensByState: Map<String, String>,
    refreshResponse: suspend RoutingContext.() -> Unit,
) {
    externalServices {
        hosts(ISSUER_URL) {
            routing {
                post("/token") {
                    val parameters = call.receiveParameters()
                    when (parameters["grant_type"]) {
                        "authorization_code" -> respondAuthorizationCodeWithIdToken(
                            parameters = parameters,
                            idTokensByState = idTokensByState,
                            accessToken = "access-token-1",
                        )

                        "refresh_token" -> refreshResponse()

                        else -> call.respond(HttpStatusCode.BadRequest)
                    }
                }
            }
        }
    }
}

internal suspend fun RoutingContext.respondRefreshedIdToken(
    keys: OpenIdTestKeys,
    subject: String = "refreshed-user",
    refreshCalls: AtomicInteger? = null,
) {
    refreshCalls?.incrementAndGet()
    call.respondText(
        openIdTestJson.encodeToString(
            TokenRefreshResponse(
                accessToken = "access-token-2",
                tokenType = "Bearer",
                refreshToken = "refresh-token-2",
                idToken = keys.idToken(subject = subject) {
                    audience = "client-id"
                },
            )
        ),
        ContentType.Application.Json,
    )
}

internal fun ApplicationTestBuilder.installSessionTestApp(
    keys: OpenIdTestKeys,
    endSessionEndpoint: String? = "$ISSUER_URL/logout",
    configureProvider: OidcProviderConfig.() -> Unit = { jwt(keys) },
    meResponse: suspend RoutingContext.(OidcToken.Id) -> Unit = { idToken ->
        call.respondText(idToken.userInfo.subject)
    },
    configureSessions: OidcSessionsConfig.() -> Unit = {},
) {
    val client = discoveryClient()
    application {
        val oidc = install(Oidc) {
            httpClient = client
            discoveryRefreshInterval = ZERO
        }

        val oidcProvider = oidc.identityProvider("auth0") {
            testIssuer(metadata = browserFlowMetadata(endSessionEndpoint))
            oauth {
                clientId = "client-id"
                clientSecret = "client-secret"
                onSuccess { call.respondText("signed in") }
                refresh()
                logout()
                sessions {
                    name = OIDC_TEST_SESSION_NAME
                    cookie {
                        cookie.secure = false
                        cookie.httpOnly = true
                    }
                    disableCsrfProtection()
                    configureSessions()
                }
            }
            configureProvider()
        }

        routing {
            authenticateWith(oidcProvider.session) {
                get("/me") {
                    val idToken = call.principal
                    meResponse(idToken)
                }
            }
        }
    }
}

internal fun TestApplicationBuilder.installOAuthExternalProvider(
    caseName: String,
    keys: OpenIdTestKeys,
    idTokensByState: Map<String, String>,
) {
    externalServices {
        hosts(ISSUER_URL) {
            routing {
                post("/token") {
                    val parameters = call.receiveParameters()
                    assertAuthorizationCodeRequest(parameters)
                    val responseParameters = when (caseName) {
                        "access" -> listOf(
                            "access_token" to keys.accessToken {
                                subject = "access-token-user"
                            },
                            "token_type" to "Bearer",
                            "expires_in" to "3600",
                        )

                        "opaque" -> listOf(
                            "access_token" to "opaque-login-token",
                            "token_type" to "Bearer",
                            "expires_in" to "3600",
                        )

                        else -> {
                            respondAuthorizationCodeWithIdToken(
                                parameters = parameters,
                                idTokensByState = idTokensByState,
                                accessToken = keys.accessToken {
                                    subject = "token-user"
                                },
                            )
                            return@post
                        }
                    }
                    call.respondText(responseParameters.formUrlEncode(), ContentType.Application.FormUrlEncoded)
                }
                post("/introspect") {
                    assertEquals("opaque-login-token", call.receiveParameters()["token"])
                    call.respondText(
                        """{"active":true,"sub":"opaque-token-user","aud":["api"],"scope":"openid"}""",
                        ContentType.Application.Json,
                    )
                }
            }
        }
    }
}

internal suspend fun HttpClient.signInWithIdToken(
    idTokensByState: MutableMap<String, String>,
    keys: OpenIdTestKeys,
    subject: String = "session-user",
    expiresIn: Duration? = null,
): String {
    val login = prepareOidcLogin()
    idTokensByState[login.state] = keys.idToken(subject = subject) {
        audience = "client-id"
        login.nonce?.let { nonce = it }
        expiresIn?.let { expiresAt = Clock.System.now().plus(it) }
    }
    val callback = completeOidcCallback(login)
    assertEquals(HttpStatusCode.OK, callback.status)
    return assertNotNull(callback.oidcSessionCookieHeader())
}

internal suspend fun HttpClient.assertMe(
    cookie: String,
    expectedStatus: HttpStatusCode,
    expectedBody: String? = null,
) {
    val response = get("/me") {
        header(HttpHeaders.Cookie, cookie)
    }
    assertEquals(expectedStatus, response.status)
    if (expectedBody != null) {
        assertEquals(expectedBody, response.bodyAsText())
    }
}
