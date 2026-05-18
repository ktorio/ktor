/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.*
import kotlin.test.*
import kotlin.time.Duration.Companion.ZERO

class OidcOAuthCallbackSecurityTest {

    @Test
    fun `oauth callback rejects id token without bearer token type`() {
        for (tokenType in listOf(null, "", "DPoP")) {
            testApplication {
                val keys = OpenIdTestKeys()
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
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()
        val missingState = "missing-state"

        openIdProvider(keys, idTokensByState)
        idTokensByState[missingState] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = "orphaned-nonce",
        )
        installOAuthProvider(keys)

        val callback = noRedirectsClient().get("/oidc/auth0/callback?code=login-code&state=$missingState")
        assertEquals(HttpStatusCode.Unauthorized, callback.status)
    }

    @Test
    fun `oauth callback rejects state without matching authorization session cookie`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)
        installOAuthProvider(keys)

        val browser = noRedirectsClient()
        val login = browser.prepareLoginAndIdToken(keys, idTokensByState)
        val withoutCookie = browser.get("/oidc/auth0/callback?code=login-code&state=${login.state}")
        assertEquals(HttpStatusCode.Unauthorized, withoutCookie.status)

        val mismatchedCookie = browser.get("/oidc/auth0/callback?code=login-code&state=${login.state}") {
            header(HttpHeaders.Cookie, "${oidcAuthorizationSessionCookieName("auth0")}=wrong")
        }
        assertEquals(HttpStatusCode.Unauthorized, mismatchedCookie.status)
    }

    @Test
    fun `oauth callback validates authorization response issuer`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()
        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    openIdDiscoveryEndpoint(
                        OpenIdProviderMetadata(
                            issuer = ISSUER_URL,
                            authorizationEndpoint = "$ISSUER_URL/authorize",
                            tokenEndpoint = "$ISSUER_URL/token",
                            jwksUri = "$ISSUER_URL/jwks",
                            authorizationResponseIssParameterSupported = true,
                        )
                    )
                    post("/token") {
                        respondAuthorizationCodeWithIdToken(
                            parameters = call.receiveParameters(),
                            idTokensByState = idTokensByState,
                            accessToken = keys.token(audience = "api", subject = "token-user"),
                        )
                    }
                }
            }
        }
        installOAuthProvider(keys, onSuccess = { principal ->
            val idToken = principal as OidcPrincipal.IdToken
            call.respondText("signed in ${idToken.userInfo.subject}")
        })

        val browser = noRedirectsClient()
        val missingIssuer = browser.prepareLoginAndIdToken(keys, idTokensByState)
        val missingIssuerCallback = browser.completeOidcCallback(missingIssuer.toPreparedLogin())
        assertEquals(HttpStatusCode.Unauthorized, missingIssuerCallback.status)

        val wrongIssuer = browser.prepareLoginAndIdToken(keys, idTokensByState)
        val wrongIssuerCallback = browser.completeOidcCallback(
            wrongIssuer.toPreparedLogin(),
            issuer = "https://wrong.example.com",
        )
        assertEquals(HttpStatusCode.Unauthorized, wrongIssuerCallback.status)

        val validIssuer = browser.prepareLoginAndIdToken(keys, idTokensByState)
        val validIssuerCallback = browser.completeOidcCallback(validIssuer.toPreparedLogin(), issuer = ISSUER_URL)
        assertEquals(HttpStatusCode.OK, validIssuerCallback.status)
        assertEquals("signed in callback-user", validIssuerCallback.bodyAsText())
    }

    @Test
    fun `oauth callback rejects id token with wrong nonce`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)
        installOAuthProvider(keys)

        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin()
        idTokensByState[login.state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = "wrong-nonce",
        )

        val callback = browser.completeOidcCallback(login)
        assertEquals(HttpStatusCode.Unauthorized, callback.status)
    }

    private fun ApplicationTestBuilder.installOAuthProvider(
        keys: OpenIdTestKeys,
        onSuccess: suspend RoutingContext.(OidcPrincipal) -> Unit = { call.respond(HttpStatusCode.OK) },
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    this.onSuccess(onSuccess)
                }
            }
        }
    }

    private suspend fun HttpClient.prepareLoginAndIdToken(
        keys: OpenIdTestKeys,
        idTokensByState: MutableMap<String, String>,
    ): PreparedSecurityLogin {
        val login = prepareOidcLogin()
        idTokensByState[login.state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = login.nonce,
        )
        return PreparedSecurityLogin(login.state, login.authorizationSessionCookie)
    }

    private data class PreparedSecurityLogin(
        val state: String,
        val authorizationSessionCookie: String,
    ) {
        fun toPreparedLogin(): PreparedLogin = PreparedLogin(
            state = state,
            nonce = null,
            authorizationSessionCookie = authorizationSessionCookie,
            authorizeUrl = Url("https://unused.example.com"),
        )
    }
}
