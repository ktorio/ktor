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
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.ZERO

class OidcOAuthCallbackTest {

    @Test
    fun `oauth callback without sessions uses onSuccess and skips session routes`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val stateCookie = assertNotNull(login.oidcStateCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        assertNotNull(authorizeUrl.parameters["code_challenge"])
        assertEquals("S256", authorizeUrl.parameters["code_challenge_method"])
        idTokensByState[state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            this.nonce = nonce
        }

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, stateCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/refresh").status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/logout").status)
    }

    @Test
    fun `oauth login can disable pkce parameters`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState, expectPkce = false)

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    codeChallengeMethod = null
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val stateCookie = assertNotNull(login.oidcStateCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        assertNull(authorizeUrl.parameters["code_challenge"])
        assertNull(authorizeUrl.parameters["code_challenge_method"])
        idTokensByState[state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            this.nonce = nonce
        }

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, stateCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())
    }

    @Test
    fun `oauth state cookie works across provider instances sharing key`() {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val stateEncryptionKey = OidcStateEncryptionKey.of(ByteArray(OidcStateEncryptionKey.KEY_SIZE) { it.toByte() })

        lateinit var loginState: String
        lateinit var loginNonce: String
        lateinit var stateCookie: String

        testApplication {
            openIdProvider(keys, idTokensByState)
            installOAuthCallbackTestProvider(keys, stateEncryptionKey)

            val browser = noRedirectsClient()
            val login = browser.prepareOidcLogin()
            loginState = login.state
            loginNonce = assertNotNull(login.nonce)
            stateCookie = login.stateCookie
        }

        idTokensByState[loginState] = keys.idToken(subject = "cluster-user") {
            audience = "client-id"
            nonce = loginNonce
        }

        testApplication {
            openIdProvider(keys, idTokensByState)
            installOAuthCallbackTestProvider(keys, stateEncryptionKey)

            val callback = noRedirectsClient().get("/oidc/auth0/callback?code=login-code&state=$loginState") {
                header(HttpHeaders.Cookie, stateCookie)
            }

            assertEquals(HttpStatusCode.OK, callback.status)
            assertEquals("signed in cluster-user", callback.bodyAsText())
        }
    }

    @Test
    fun `oauth state cookie secure flag follows request scheme`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)
        installOAuthCallbackTestProvider(keys)

        val browser = noRedirectsClient()
        val httpLogin = browser.get("/oidc/auth0/login")
        val httpCookie = parseServerSetCookieHeader(
            assertNotNull(
                httpLogin.headers.getAll(HttpHeaders.SetCookie)
                    .orEmpty()
                    .firstOrNull { it.startsWith(OidcStateCookieName) }
            )
        )
        assertEquals(false, httpCookie.secure)

        val httpsLogin = browser.get("/oidc/auth0/login") {
            url {
                protocol = URLProtocol.HTTPS
                port = 443
            }
            header(HttpHeaders.Host, "app.example.com:443")
        }
        val httpsCookie = parseServerSetCookieHeader(
            assertNotNull(
                httpsLogin.headers.getAll(HttpHeaders.SetCookie)
                    .orEmpty()
                    .firstOrNull { it.startsWith(OidcStateCookieName) }
            )
        )
        assertEquals(true, httpsCookie.secure)
    }

    @Test
    fun `oauth error callback uses configured failure handler`() = testApplication {
        application {
            val oidc = openIdConnect { }
            oidc.provider("auth0") {
                testIssuer()
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
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState, tokenType = "bearer")

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val stateCookie = assertNotNull(login.oidcStateCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        idTokensByState[state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            this.nonce = nonce
        }

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, stateCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())
    }

    @Test
    fun `oauth redirect uri uses request origin and omits default ports`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()
        val tokenRedirectUris = mutableListOf<String?>()
        val tokenResources = mutableListOf<List<String>>()

        externalServices {
            hosts(ISSUER_URL) {
                routing {
                    post("/token") {
                        val parameters = call.receiveParameters()
                        tokenRedirectUris += parameters["redirect_uri"]
                        tokenResources += parameters.getAll("resource").orEmpty()
                        respondAuthorizationCodeWithIdToken(
                            parameters = parameters,
                            idTokensByState = idTokensByState,
                            accessToken = keys.accessToken {
                                subject = "token-user"
                            },
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
                testIssuer(metadata = browserFlowMetadata())
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    resourceIndicators = listOf("https://api.example.com", "https://mcp.example.com")
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
        assertEquals(
            listOf("https://api.example.com", "https://mcp.example.com"),
            login.authorizeUrl.parameters.getAll("resource"),
        )

        idTokensByState[login.state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            nonce = login.nonce
        }

        val callback = browser.completeOidcCallback(login) {
            url {
                protocol = URLProtocol.HTTPS
                port = 443
            }
            header(HttpHeaders.Host, "app.example.com:443")
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals(listOf<String?>("https://app.example.com/oidc/auth0/callback"), tokenRedirectUris)
        assertEquals(listOf(listOf("https://api.example.com", "https://mcp.example.com")), tokenResources)
    }

    @Test
    fun `oauth and bearer work together without sessions and do not install session routes`() = testApplication {
        val keys = testRsaKeys
        val idTokensByState = ConcurrentHashMap<String, String>()

        openIdProvider(keys, idTokensByState)

        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            val oidcProvider = oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                accessToken {
                    audiences = setOf("api")
                }
                bearer()
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }

            routing {
                authenticateWith(oidcProvider.bearer) {
                    get("/protected") {
                        val accessToken = principal as OidcToken.Access
                        call.respondText(accessToken.userInfo?.subject ?: "missing")
                    }
                }
            }
        }

        val browser = noRedirectsClient()
        val login = browser.get("/oidc/auth0/login")
        assertEquals(HttpStatusCode.Found, login.status)
        val authorizeUrl = Url(assertNotNull(login.headers[HttpHeaders.Location]))
        val stateCookie = assertNotNull(login.oidcStateCookieHeader())
        val state = assertNotNull(authorizeUrl.parameters["state"])
        val nonce = assertNotNull(authorizeUrl.parameters["nonce"])
        idTokensByState[state] = keys.idToken(subject = "callback-user") {
            audience = "client-id"
            this.nonce = nonce
        }

        val callback = browser.get("/oidc/auth0/callback?code=login-code&state=$state") {
            header(HttpHeaders.Cookie, stateCookie)
        }
        assertEquals(HttpStatusCode.OK, callback.status)
        assertEquals("signed in callback-user", callback.bodyAsText())

        val bearerToken = keys.accessToken {
            subject = "api-user"
        }
        val protected = client.get("/protected") {
            header(HttpHeaders.Authorization, "Bearer $bearerToken")
        }
        assertEquals(HttpStatusCode.OK, protected.status)
        assertEquals("api-user", protected.bodyAsText())

        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/refresh").status)
        assertEquals(HttpStatusCode.NotFound, browser.post("/oidc/auth0/logout").status)
    }

    private fun ApplicationTestBuilder.installOAuthCallbackTestProvider(
        keys: OpenIdTestKeys,
        stateEncryptionKey: OidcStateEncryptionKey? = null,
    ) {
        val openIdClient = openIdHttpClient()
        application {
            val oidc = openIdConnect {
                httpClient = openIdClient
                discoveryRefreshInterval = ZERO
            }
            oidc.provider("auth0") {
                testIssuer()
                jwt(keys)
                oauth {
                    clientId = "client-id"
                    clientSecret = "client-secret"
                    this.stateEncryptionKey = stateEncryptionKey
                    onSuccess { principal ->
                        val idToken = principal as OidcToken.Id
                        call.respondText("signed in ${idToken.userInfo.subject}")
                    }
                }
            }
        }
    }
}
