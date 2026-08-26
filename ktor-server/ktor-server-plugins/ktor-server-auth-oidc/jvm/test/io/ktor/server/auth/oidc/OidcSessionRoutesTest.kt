/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.response.*
import io.ktor.server.testing.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class OidcSessionRoutesTest {

    @Test
    fun `auto refresh updates session before user route handler`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 30.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 10.seconds)

        browser.assertMe(cookie, HttpStatusCode.OK, "refreshed-user")
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `auto refresh shares in flight refresh for same refresh token`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 30.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 10.seconds)

        coroutineScope {
            List(2) {
                launch {
                    browser.assertMe(cookie, HttpStatusCode.OK, "refreshed-user")
                }
            }.joinAll()
        }
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `auto refresh shares completed refresh for same refresh token`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 30.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 10.seconds)

        browser.assertMe(cookie, HttpStatusCode.OK, "refreshed-user")
        browser.assertMe(cookie, HttpStatusCode.OK, "refreshed-user")

        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `auto refresh keeps session when token is outside refresh window`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            refreshCalls.incrementAndGet()
            call.respond(HttpStatusCode.InternalServerError)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 5.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 60.seconds)

        browser.assertMe(cookie, HttpStatusCode.OK, "session-user")
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun `disabled refresh rejects expired session on user route`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdRefreshProvider(idTokensByState) {
            call.respond(HttpStatusCode.InternalServerError)
        }
        installSessionTestApp(keys)

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = (-1).seconds)

        browser.assertMe(cookie, HttpStatusCode.Unauthorized)
    }

    @Test
    fun `logout route clears expired session`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdRefreshProvider(idTokensByState) {
            call.respond(HttpStatusCode.InternalServerError)
        }
        installSessionTestApp(keys)

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = (-1).seconds)

        val logout = browser.post("/oidc/auth0/logout") {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.SeeOther, logout.status)

        browser.assertMe(cookie, HttpStatusCode.Unauthorized)
    }

    @Test
    fun `explicit refresh route can refresh expired session`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys)

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = (-1).seconds)

        val refresh = browser.post("/oidc/auth0/refresh") {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        assertEquals(1, refreshCalls.get())

        val refreshedCookie = refresh.oidcSessionCookieHeader() ?: cookie
        browser.assertMe(refreshedCookie, HttpStatusCode.OK, "refreshed-user")
    }

    @Test
    fun `explicit refresh route is not auto refreshed`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 30.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 10.seconds)

        val refresh = browser.post("/oidc/auth0/refresh") {
            header(HttpHeaders.Cookie, cookie)
        }
        assertEquals(HttpStatusCode.OK, refresh.status)
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun `auto refresh clears session when refreshed response has no id token`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdRefreshProvider(idTokensByState) {
            call.respondText(
                openIdTestJson.encodeToString(
                    TokenRefreshResponse(accessToken = "access-token-only", tokenType = "Bearer")
                ),
                ContentType.Application.Json,
            )
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Auto(beforeExpiry = 30.seconds)
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys, expiresIn = 10.seconds)

        browser.assertMe(cookie, HttpStatusCode.Unauthorized)
        browser.assertMe(cookie, HttpStatusCode.Unauthorized)
    }

    @Test
    fun `custom refresh can keep or refresh session`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val refreshCalls = AtomicInteger()
        val keepCurrent = AtomicInteger()

        openIdRefreshProvider(idTokensByState) {
            respondRefreshedIdToken(keys, refreshCalls = refreshCalls)
        }
        installSessionTestApp(keys) {
            tokenRefreshStrategy = OidcTokenRefreshStrategy.Custom { provider, token, _ ->
                when (keepCurrent.getAndIncrement()) {
                    0 -> token
                    1 -> provider.refreshToken(checkNotNull(token.refreshToken)).idToken
                    else -> null
                }
            }
        }

        val browser = noRedirectsClient()
        val cookie = browser.signInWithIdToken(idTokensByState, keys)

        browser.assertMe(cookie, HttpStatusCode.OK, "session-user")
        assertEquals(0, refreshCalls.get())

        browser.assertMe(cookie, HttpStatusCode.OK, "refreshed-user")
        assertEquals(1, refreshCalls.get())

        browser.assertMe(cookie, HttpStatusCode.Unauthorized)
    }

    @Test
    fun `session refresh rejects invalid refreshed principals and keeps existing session`() {
        val cases = listOf(
            "missing id token" to { _: OpenIdTestKeys ->
                TokenRefreshResponse(accessToken = "access-token-only", tokenType = "Bearer")
            },
            "id token contains nonce" to { keys: OpenIdTestKeys ->
                TokenRefreshResponse(
                    accessToken = "access-token-2",
                    tokenType = "Bearer",
                    idToken = keys.idToken(subject = "refreshed-user") {
                        audience = "client-id"
                        nonce = "unexpected-nonce"
                    },
                )
            },
        )

        for ((caseName, response) in cases) {
            testApplication {
                val keys = testRsaKeys
                val idTokensByState = ConcurrentHashMap<String, String>()
                openIdRefreshProvider(idTokensByState) {
                    call.respondText(openIdTestJson.encodeToString(response(keys)), ContentType.Application.Json)
                }
                installSessionTestApp(keys)

                val browser = noRedirectsClient()
                val cookie = browser.signInWithIdToken(idTokensByState, keys)
                val refresh = browser.post("/oidc/auth0/refresh") {
                    header(HttpHeaders.Cookie, cookie)
                }
                assertEquals(HttpStatusCode.Unauthorized, refresh.status, caseName)

                browser.assertMe(cookie, HttpStatusCode.OK, "session-user")
            }
        }
    }

    @Test
    fun `logout omits post_logout_redirect_uri by default`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        openIdProvider(keys, idTokensByState)
        installSessionTestApp(
            keys = keys,
            meResponse = { call.respondText("ok") },
        )

        val browser = noRedirectsClient()
        val sessionCookie = browser.signInWithIdToken(idTokensByState, keys)

        val logout = browser.post("/oidc/auth0/logout") {
            header(HttpHeaders.Cookie, sessionCookie)
            header(HttpHeaders.Host, "foodies.local")
            header(HttpHeaders.Origin, "http://foodies.local")
        }
        assertEquals(HttpStatusCode.SeeOther, logout.status)
        val logoutUrl = Url(assertNotNull(logout.headers[HttpHeaders.Location]))
        assertNull(logoutUrl.parameters["post_logout_redirect_uri"])
    }

    @Test
    fun `logout post_logout_redirect_uri reflects incoming request host when configured`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        openIdProvider(keys, idTokensByState)
        val openIdClient = discoveryClient()
        application {
            val oidc = install(Oidc) {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.identityProvider("auth0") {
                testIssuer(metadata = browserFlowMetadata())
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onAuthenticated { call.respondText("signed in") }
                    sessions {
                        name = OIDC_TEST_SESSION_NAME
                        cookie {
                            cookie.secure = false
                            cookie.httpOnly = true
                        }
                        disableCsrfProtection()
                    }
                    logout(
                        path = "/oidc/auth0/logout",
                        postLogoutRedirectUri = { path("/") },
                    )
                }
            }
        }

        val browser = noRedirectsClient()
        val sessionCookie = browser.signInWithIdToken(idTokensByState, keys)

        val logout = browser.post("/oidc/auth0/logout") {
            header(HttpHeaders.Cookie, sessionCookie)
            header(HttpHeaders.Host, "foodies.local")
            header(HttpHeaders.Origin, "http://foodies.local")
        }
        assertEquals(HttpStatusCode.SeeOther, logout.status)
        val logoutUrl = Url(assertNotNull(logout.headers[HttpHeaders.Location]))
        assertEquals("http://foodies.local/", logoutUrl.parameters["post_logout_redirect_uri"])
    }

    @Test
    fun `session oauth callback without id token rejects access and opaque tokens`() {
        val cases = listOf("access", "opaque")
        for (caseName in cases) {
            testApplication {
                val keys = testRsaKeys
                installOAuthExternalProvider(caseName, keys, emptyMap())
                installSessionTestApp(
                    keys = keys,
                    configureProvider = {
                        when (caseName) {
                            "access" -> {
                                jwt(keys)
                                bearer {
                                    audience = setOf("api")
                                }
                            }

                            "opaque" -> bearer {
                                audience = setOf("api")
                                introspection {
                                    endpoint = "$ISSUER_URL/introspect"
                                    clientId = "resource-server"
                                    clientSecret = "secret"
                                }
                            }

                            else -> jwt(keys)
                        }
                    },
                    meResponse = { idToken ->
                        call.respondText("id:${idToken.userInfo.subject}")
                    },
                )

                val browser = noRedirectsClient()
                val login = browser.prepareOidcLogin()
                val callback = browser.completeOidcCallback(login)
                assertEquals(HttpStatusCode.Unauthorized, callback.status, caseName)
                assertNull(callback.oidcSessionCookieHeader(), caseName)

                val profile = browser.get("/me")
                assertEquals(HttpStatusCode.Unauthorized, profile.status, caseName)
            }
        }
    }

    @Test
    fun `logout route setup fails when IDP does not have end session`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        installOAuthExternalProvider("id-without-end-session", keys, idTokensByState)

        val failure = assertFailsWith<IllegalArgumentException> {
            installSessionTestApp(
                keys = keys,
                endSessionEndpoint = null,
            )
            startApplication()
        }
        assertContains(failure.message!!, "doesn't support logout")
    }
}
