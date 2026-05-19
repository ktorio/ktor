/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.openid

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.openid.utils.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.ZERO

class OidcOAuthCallbackTest {

    @Test
    fun `oauth callback without sessions uses onSuccess and skips session routes`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)

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
                    onSuccess { principal ->
                        val idToken = principal as OidcPrincipal.IdToken
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val authorizationSessionCookie = assertNotNull(login.oidcAuthorizationSessionCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        idTokensByState[state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = nonce,
        )

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, authorizationSessionCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/refresh").status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/logout").status)
    }

    @Test
    fun `oauth error callback uses configured failure handler`() = testApplication {
        openIdProvider()

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                issuer = ISSUER_URL
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onFailure { cause ->
                        val message = (cause as? AuthenticationFailedCause.Error)?.message ?: cause.toString()
                        call.respondText("failed:$message")
                    }
                }
            }
        }

        val response = client.get(
            "/oidc/auth0/callback?error=access_denied&error_description=Resource%20owner%20denied"
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("failed:access_denied: Resource owner denied", response.bodyAsText())
    }

    @Test
    fun `oauth callback accepts lowercase bearer token type with id token`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState, tokenType = "bearer")

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
                    onSuccess { principal ->
                        val idToken = principal as OidcPrincipal.IdToken
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val authorizationSessionCookie = assertNotNull(login.oidcAuthorizationSessionCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        idTokensByState[state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = nonce,
        )

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, authorizationSessionCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())
    }

    @Test
    fun `oauth redirect uri uses request origin and omits default ports`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()
        val tokenRedirectUris = mutableListOf<String?>()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    openIdDiscoveryEndpoint(browserFlowMetadata())
                    post("/token") {
                        val parameters = call.receiveParameters()
                        tokenRedirectUris += parameters["redirect_uri"]
                        respondAuthorizationCodeWithIdToken(
                            parameters = parameters,
                            idTokensByState = idTokensByState,
                            accessToken = keys.token(audience = "api", subject = "token-user"),
                        )
                    }
                }
            }
        }

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
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.prepareOidcLogin {
            url {
                protocol = URLProtocol.HTTPS
                port = 443
            }
            header(HttpHeaders.Host, "app.example.com:443")
        }
        assertEquals("https://app.example.com/oidc/auth0/callback", login.authorizeUrl.parameters["redirect_uri"])

        idTokensByState[login.state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = login.nonce,
        )

        val callback = browser.completeOidcCallback(login) {
            url {
                protocol = URLProtocol.HTTPS
                port = 443
            }
            header(HttpHeaders.Host, "app.example.com:443")
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals(listOf<String?>("https://app.example.com/oidc/auth0/callback"), tokenRedirectUris)
    }

    @Test
    fun `oauth and bearer work together without sessions and do not install session routes`() = testApplication {
        val keys = OpenIdTestKeys()
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("auth0") {
                issuer = ISSUER_URL
                jwt {
                    jwkProviderFactory = { keys.jwkProvider }
                }
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onSuccess { principal ->
                        val idToken = principal as OidcPrincipal.IdToken
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcPrincipal.AccessToken
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val authorizationSessionCookie = assertNotNull(login.oidcAuthorizationSessionCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        idTokensByState[state] = keys.token(
            audience = "client-id",
            subject = "callback-user",
            nonce = nonce,
        )

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, authorizationSessionCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())

        val bearerToken = keys.token(audience = "api", subject = "api-user")
        val protected = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
        assertEquals(HttpStatusCode.OK, protected.status)
        assertEquals("api-user", protected.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/refresh").status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/logout").status)
    }
}
