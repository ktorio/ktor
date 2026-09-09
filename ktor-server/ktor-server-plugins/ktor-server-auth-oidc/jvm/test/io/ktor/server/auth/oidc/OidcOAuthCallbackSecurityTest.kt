/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.oidc.utils.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.ExperimentalTime

class OidcOAuthCallbackSecurityTest {

    @Test
    fun `oauth callback rejects id token without bearer token type`() {
        for (tokenType in listOf(null, "", "DPoP")) {
            testApplication {
                val keys = testRsaKeys
                val idTokensByState = ConcurrentHashMap<String, String>()
                openIdProvider(keys, idTokensByState, tokenType = tokenType)
                installOAuthProvider(keys)

                val browser = noRedirectsClient()
                val login = browser.prepareLoginAndIdToken(keys, idTokensByState)
                val callback = browser.completeOidcCallback(login.toPreparedLogin())
                assertEquals(HttpStatusCode.Unauthorized, callback.status)
            }
        }
    }

    @Test
    fun `oauth callback rejects id token when stored nonce is missing`() = testApplication {
        val idTokensByState = ConcurrentHashMap<String, String>()
        val missingState = "missing-state"

        openIdProvider(testRsaKeys, idTokensByState)
        idTokensByState[missingState] = testRsaKeys.idToken(subject = "callback-user") {
            audience = "client-id"
            nonce = "orphaned-nonce"
        }
        installOAuthProvider(testRsaKeys)

        val callback = noRedirectsClient().get("/oidc/auth0/callback?code=login-code&state=$missingState")
        assertEquals(HttpStatusCode.Unauthorized, callback.status)
    }

    @Test
    fun `oauth callback rejects state without matching authorization session cookie`() = testApplication {
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(testRsaKeys, idTokensByState)
        installOAuthProvider(testRsaKeys)

        val browser = noRedirectsClient()
        val login = browser.prepareLoginAndIdToken(testRsaKeys, idTokensByState)
        val withoutCookie = browser.get("/oidc/auth0/callback?code=login-code&state=${login.state}")
        assertEquals(HttpStatusCode.Unauthorized, withoutCookie.status)

        val mismatchedCookie = browser.get("/oidc/auth0/callback?code=login-code&state=${login.state}") {
            header(HttpHeaders.Cookie, "${oidcStateCookieName("auth0")}=wrong")
        }
        assertEquals(HttpStatusCode.Unauthorized, mismatchedCookie.status)
    }

    @Test
    fun `oauth callback validates authorization response issuer`() = testApplication {
        val idTokensByState = ConcurrentHashMap<String, String>()
        val metadata = OpenIdProviderMetadata(
            issuer = ISSUER_URL,
            authorizationEndpoint = "$ISSUER_URL/authorize",
            tokenEndpoint = "$ISSUER_URL/token",
            jwksUri = "$ISSUER_URL/jwks",
            authorizationResponseIssParameterSupported = true,
        )
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    post("/token") {
                        respondAuthorizationCodeWithIdToken(
                            parameters = call.receiveParameters(),
                            idTokensByState = idTokensByState,
                            accessToken = testRsaKeys.accessToken {
                                subject = "token-user"
                            },
                        )
                    }
                }
            }
        }
        installOAuthProvider(
            keys = testRsaKeys,
            metadata = metadata,
            onAuthenticated = { idToken ->
                call.respondText("signed in ${idToken.userInfo.subject}")
            },
        )

        val browser = noRedirectsClient()
        val missingIssuer = browser.prepareLoginAndIdToken(testRsaKeys, idTokensByState)
        val missingIssuerCallback = browser.completeOidcCallback(missingIssuer.toPreparedLogin())
        assertEquals(HttpStatusCode.Unauthorized, missingIssuerCallback.status)

        val wrongIssuer = browser.prepareLoginAndIdToken(testRsaKeys, idTokensByState)
        val wrongIssuerCallback = browser.completeOidcCallback(
            wrongIssuer.toPreparedLogin(),
            issuer = "https://wrong.example.com",
        )
        assertEquals(HttpStatusCode.Unauthorized, wrongIssuerCallback.status)

        val validIssuer = browser.prepareLoginAndIdToken(testRsaKeys, idTokensByState)
        val validIssuerCallback = browser.completeOidcCallback(validIssuer.toPreparedLogin(), issuer = ISSUER_URL)
        assertEquals(HttpStatusCode.OK, validIssuerCallback.status)
        assertEquals("signed in callback-user", validIssuerCallback.bodyAsText())
    }

    @Test
    fun `oauth callback rejects id token with wrong nonce`() = testApplication {
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(testRsaKeys, idTokensByState)
        installOAuthProvider(testRsaKeys)

        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin()
        idTokensByState[login.state] = testRsaKeys.idToken(subject = "callback-user") {
            audience = "client-id"
            nonce = "wrong-nonce"
        }

        val callback = browser.completeOidcCallback(login)
        assertEquals(HttpStatusCode.Unauthorized, callback.status)
    }

    @Test
    fun `oauth callback validates azp claim of id token`() {
        data class AzpCase(val audiences: List<String>, val azp: String?, val expectedStatus: HttpStatusCode)

        val cases = listOf(
            AzpCase(listOf("client-id", "other-client"), azp = null, HttpStatusCode.Unauthorized),
            AzpCase(listOf("client-id"), azp = "other-client", HttpStatusCode.Unauthorized),
            AzpCase(listOf("client-id", "other-client"), azp = "other-client", HttpStatusCode.Unauthorized),
            AzpCase(listOf("client-id", "other-client"), azp = "client-id", HttpStatusCode.OK),
        )

        for (case in cases) {
            testApplication {
                val idTokensByState = ConcurrentHashMap<String, String>()

                openIdProvider(testRsaKeys, idTokensByState)
                installOAuthProvider(testRsaKeys)

                val browser = noRedirectsClient()
                val login = browser.prepareOidcLogin()
                idTokensByState[login.state] = testRsaKeys.idToken(subject = "callback-user") {
                    audience = "client-id"
                    claim("aud", case.audiences)
                    case.azp?.let { claim("azp", it) }
                    nonce = login.nonce
                }

                val callback = browser.completeOidcCallback(login)
                assertEquals(case.expectedStatus, callback.status, "Unexpected status for $case")
            }
        }
    }

    @Test
    fun `oauth callback rejects id token missing exp or iat claim`() {
        val cases = listOf<OpenIdTestIdTokenBuilder.() -> Unit>(
            { expiresAt = null },
            { issuedAt = null },
        )
        for (omitClaim in cases) {
            testApplication {
                val idTokensByState = ConcurrentHashMap<String, String>()

                openIdProvider(testRsaKeys, idTokensByState)
                installOAuthProvider(testRsaKeys)

                val browser = noRedirectsClient()
                val login = browser.prepareOidcLogin()
                idTokensByState[login.state] = testRsaKeys.idToken(subject = "callback-user") {
                    audience = "client-id"
                    nonce = login.nonce
                    omitClaim()
                }

                val callback = browser.completeOidcCallback(login)
                assertEquals(HttpStatusCode.Unauthorized, callback.status)
            }
        }
    }

    @Test
    fun `oauth callback rotates session id planted before login`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdRefreshProvider(idTokensByState) { call.respond(HttpStatusCode.BadRequest) }
        installSessionTestApp(keys)

        val browser = noRedirectsClient()

        // The attacker signs in to obtain a session cookie known to them.
        val plantedCookie = browser.signInWithIdToken(idTokensByState, keys, subject = "attacker")

        // The victim signs in while the attacker's session cookie is planted in their browser.
        val victimLogin = browser.prepareOidcLogin()
        idTokensByState[victimLogin.state] = keys.idToken(subject = "victim") {
            audience = "client-id"
            victimLogin.nonce?.let { nonce = it }
        }
        val victimCallback = browser.completeOidcCallback(victimLogin) {
            header(HttpHeaders.Cookie, plantedCookie)
        }
        assertEquals(HttpStatusCode.OK, victimCallback.status)
        val victimCookie = assertNotNull(victimCallback.oidcSessionCookieHeader())

        assertNotEquals(
            plantedCookie,
            victimCookie,
            "authenticated session must not be stored under a pre-login session ID",
        )
        browser.assertMe(victimCookie, HttpStatusCode.OK, "victim")
        browser.assertMe(plantedCookie, HttpStatusCode.Unauthorized)
    }

    private fun ApplicationTestBuilder.installOAuthProvider(
        keys: OpenIdTestKeys,
        metadata: OpenIdProviderMetadata = testOpenIdProviderMetadata(ISSUER_URL),
        onAuthenticated: suspend RoutingContext.(OidcToken.Id) -> Unit = { call.respond(HttpStatusCode.OK) },
    ) {
        val openIdClient = discoveryClient()
        application {
            val oidc = install(Oidc) {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.identityProvider("auth0") {
                testIssuer(metadata = metadata)
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    this.onAuthenticated { token -> onAuthenticated(token) }
                }
            }
        }
    }

    private suspend fun HttpClient.prepareLoginAndIdToken(
        keys: OpenIdTestKeys,
        idTokensByState: MutableMap<String, String>,
    ): PreparedSecurityLogin {
        val login = prepareOidcLogin()
        idTokensByState[login.state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            nonce = login.nonce
        }
        return PreparedSecurityLogin(login.state, login.stateCookie)
    }

    private data class PreparedSecurityLogin(
        val state: String,
        val stateCookie: String,
    ) {
        fun toPreparedLogin(): PreparedLogin = PreparedLogin(
            state = state,
            nonce = null,
            stateCookie = stateCookie,
            authorizeUrl = Url("https://unused.example.com"),
        )
    }
}
