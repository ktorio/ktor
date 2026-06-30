/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OidcTypedAuthenticationTest {

    @Test
    fun `typed bearer and session schemes share provider principal type`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
            }
            val google = oidc.provider(
                name = "google",
                transformPrincipal = { principal ->
                    when (principal) {
                        is OidcToken.Id -> UserIdPrincipal(principal.userInfo.subject)
                        is OidcToken.Access -> principal.userInfo?.subject?.let(::UserIdPrincipal)
                        is OidcToken.Opaque -> principal.introspection.subject?.let(::UserIdPrincipal)
                    }
                },
            ) {
                testIssuer()
                jwt(keys)
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
                sessions {
                    name = OIDC_TEST_SESSION_NAME
                    cookie {
                        cookie.secure = false
                    }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                }
            }

            routing {
                authenticateWithAnyOf<UserIdPrincipal>(google.bearer, google.sessions) {
                    get("/both") {
                        call.respondText(principal.name)
                    }
                }
            }
        }

        val bearerToken = keys.accessToken {
            subject = "bearer-user"
        }
        val bearerResponse = client.get("/both") {
            header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
        assertEquals("bearer-user", bearerResponse.bodyAsText())

        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin("google")
        idTokensByState[login.state] = keys.idToken(subject = "session-user") {
            audience = "client-id"
            nonce = login.nonce
        }

        val callback = browser.completeOidcCallback(login, providerName = "google")
        val sessionCookie = assertNotNull(callback.oidcSessionCookieHeader())
        val sessionResponse = client.get("/both") {
            header(HttpHeaders.Cookie, sessionCookie)
        }
        assertEquals("session-user", sessionResponse.bodyAsText())
    }
}
