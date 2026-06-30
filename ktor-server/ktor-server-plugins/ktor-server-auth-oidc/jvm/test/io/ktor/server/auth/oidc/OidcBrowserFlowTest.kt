/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcBrowserFlowTest {

    @Test
    fun `browser can sign in use session refresh and logout`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val tokenGrantTypes = mutableListOf<String>()
        val userInfoCalls = AtomicInteger()
        val refreshedIdToken = AtomicReference<String>()

        openIdBrowserFlowProvider(keys, idTokensByState, tokenGrantTypes, userInfoCalls, refreshedIdToken)

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
                sessions {
                    name = OIDC_TEST_SESSION_NAME
                    cookie {
                        cookie.secure = false
                        cookie.httpOnly = true
                    }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    fetchUserInfo = true
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }

            routing {
                authenticateWithAnyOf(oidcProvider.bearer, oidcProvider.sessions) {
                    get("/either") {
                        val subject = when (val currentPrincipal = principal) {
                            is OidcToken.Access -> "bearer:${currentPrincipal.userInfo?.subject}"
                            is OidcToken.Id -> "session:${currentPrincipal.userInfo.subject}"
                            is OidcToken.Opaque -> "opaque"
                        }
                        call.respondText(subject)
                    }
                }
                authenticateWith(oidcProvider.sessions) {
                    get("/me") {
                        val idToken = principal as OidcToken.Id
                        call.respondText("${idToken.userInfo.subject}:${idToken.userInfo.name}")
                    }
                    post("/me") {
                        val idToken = principal as OidcToken.Id
                        call.respondText("updated ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        var sessionCookie: String? = null

        fun HttpRequestBuilder.sessionCookie() {
            sessionCookie?.let { header(HttpHeaders.Cookie, it) }
        }

        val browser = noRedirectsClient()

        val apiToken = keys.accessToken {
            subject = "api-user"
        }
        val bearerOnly = browser.get("/either") {
            header(HttpHeaders.Authorization, "Bearer $apiToken")
        }
        assertEquals(HttpStatusCode.OK, bearerOnly.status)
        assertEquals("bearer:api-user", bearerOnly.bodyAsText())

        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val stateCookie = assertNotNull(login.oidcStateCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        assertNotNull(authorizeUrl.parameters["code_challenge"])
        assertEquals("S256", authorizeUrl.parameters["code_challenge_method"])
        assertEquals("auth0.example.com", authorizeUrl.host)
        assertEquals("/authorize", authorizeUrl.encodedPath)

        idTokensByState[state] = keys.idToken(subject = "browser-user") {
            audience = "client-id"
            this.nonce = nonce
        }

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, stateCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in browser-user", callback.bodyAsText())
        assertEquals(listOf("authorization_code"), tokenGrantTypes)
        assertEquals(1, userInfoCalls.get())
        sessionCookie = assertNotNull(callback.oidcSessionCookieHeader())
        val setSessionCookie = assertNotNull(
            callback.headers.getAll(HttpHeaders.SetCookie)
                .orEmpty()
                .firstOrNull { it.startsWith("$OIDC_TEST_SESSION_NAME=") }
        )
        val parsedSessionCookie = parseServerSetCookieHeader(setSessionCookie)
        assertTrue(parsedSessionCookie.httpOnly)
        assertEquals("lax", parsedSessionCookie.extensions["SameSite"])

        val sessionOnly = browser.get("/either") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.OK, sessionOnly.status)
        assertEquals("session:browser-user", sessionOnly.bodyAsText())

        val bearerPreferred = browser.get("/either") {
            sessionCookie()
            header(HttpHeaders.Authorization, "Bearer $apiToken")
        }
        assertEquals(HttpStatusCode.OK, bearerPreferred.status)
        assertEquals("bearer:api-user", bearerPreferred.bodyAsText())

        val profile = browser.get("/me") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.OK, profile.status)
        assertEquals("browser-user:Browser User", profile.bodyAsText())

        val blockedProfileUpdate = browser.post("/me") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.BadRequest, blockedProfileUpdate.status)

        val allowedProfileUpdate = browser.post("/me") {
            sessionCookie()
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost")
        }
        assertEquals(HttpStatusCode.OK, allowedProfileUpdate.status)
        assertEquals("updated browser-user", allowedProfileUpdate.bodyAsText())

        val blockedRefresh = browser.post("/oidc/auth0/refresh") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.BadRequest, blockedRefresh.status)

        val refresh = browser.post("/oidc/auth0/refresh") {
            sessionCookie()
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost")
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        assertEquals(listOf("authorization_code", "refresh_token"), tokenGrantTypes)
        assertEquals(2, userInfoCalls.get())
        refresh.oidcSessionCookieHeader()?.let { sessionCookie = it }

        val refreshedProfile = browser.get("/me") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.OK, refreshedProfile.status)
        assertEquals("refreshed-user:Refreshed User", refreshedProfile.bodyAsText())

        val logout = browser.post("/oidc/auth0/logout") {
            sessionCookie()
            header(HttpHeaders.Host, "localhost")
            header(HttpHeaders.Origin, "http://localhost")
        }
        assertEquals(HttpStatusCode.SeeOther, logout.status)
        val logoutUrl = Url(assertNotNull(logout.headers[HttpHeaders.Location]))
        assertEquals("/logout", logoutUrl.encodedPath)
        assertEquals(logoutUrl.parameters["id_token_hint"], refreshedIdToken.get())
        assertEquals("http://localhost/", logoutUrl.parameters["post_logout_redirect_uri"])
        assertEquals("client-id", logoutUrl.parameters["client_id"])

        val afterLogout = browser.get("/me") {
            sessionCookie()
        }
        assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
    }

    private fun TestApplicationBuilder.openIdBrowserFlowProvider(
        keys: OpenIdTestKeys,
        idTokensByState: Map<String, String>,
        tokenGrantTypes: MutableList<String>,
        userInfoCalls: AtomicInteger,
        refreshedIdToken: AtomicReference<String>,
    ) {
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    tokenEndpoint(keys, idTokensByState, tokenGrantTypes, refreshedIdToken)
                    userInfoEndpoint(userInfoCalls)
                }
            }
        }
    }

    private fun Route.tokenEndpoint(
        keys: OpenIdTestKeys,
        idTokensByState: Map<String, String>,
        tokenGrantTypes: MutableList<String>,
        refreshedIdToken: AtomicReference<String>,
    ) {
        post("/token") {
            val parameters = call.receiveParameters()
            val grantType = assertNotNull(parameters["grant_type"])
            tokenGrantTypes += grantType

            when (grantType) {
                "authorization_code" -> {
                    respondAuthorizationCodeWithIdToken(
                        parameters = parameters,
                        idTokensByState = idTokensByState,
                        accessToken = "access-token-1",
                    )
                }

                "refresh_token" -> {
                    assertEquals(parameters["refresh_token"], "refresh-token-1")
                    assertEquals("client-id", parameters["client_id"])
                    assertEquals("client-secret", parameters["client_secret"])

                    val idToken = keys.idToken(subject = "refreshed-user") {
                        audience = "client-id"
                        name = "Refreshed Token Subject"
                    }
                    refreshedIdToken.set(idToken)
                    call.respondText(
                        openIdTestJson.encodeToString(
                            TokenRefreshResponse(
                                accessToken = "access-token-2",
                                tokenType = "Bearer",
                                expiresIn = 3600,
                                refreshToken = "refresh-token-2",
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

    private fun Route.userInfoEndpoint(userInfoCalls: AtomicInteger) {
        get("/userinfo") {
            userInfoCalls.incrementAndGet()
            val body = when (call.request.headers[HttpHeaders.Authorization]) {
                "Bearer access-token-1" -> {
                    """{"sub":"browser-user","name":"Browser User","email":"browser@example.com"}"""
                }

                "Bearer access-token-2" -> {
                    """{"sub":"refreshed-user","name":"Refreshed User","email":"refresh@example.com"}"""
                }

                else -> return@get call.respond(HttpStatusCode.Unauthorized)
            }
            call.respondText(body, ContentType.Application.Json)
        }
    }
}
