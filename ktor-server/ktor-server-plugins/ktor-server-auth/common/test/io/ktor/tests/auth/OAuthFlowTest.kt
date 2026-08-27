/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

@Serializable
data class OAuthSession(val accessToken: String)

data class OAuthPrincipal(val token: String, val source: String)

class OAuthFlowTest {

    private fun createOAuth(testClient: HttpClient) =
        oauth2Session<OAuthPrincipal, OAuthSession>("test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginPath = "/login"
            callback("/callback") {
                val user = call.principal
                call.respondText(call.session.accessToken + ":" + user.token + ":" + user.source)
            }
            sessions {
                transport = SessionTransportType.Cookie()
                sessionCreator = { token ->
                    OAuthSession(token.accessToken)
                }
                validate { OAuthPrincipal(it.accessToken, source = "session") }
            }
        }

    private fun ApplicationTestBuilder.mockOAuthServices(accessToken: String = "test_token") {
        externalServices {
            hosts("http://oauth.test") {
                routing {
                    get("/authorize") {
                        val state = call.parameters["state"]!!
                        call.respondText(
                            "code=test_code&state=$state",
                            ContentType.Application.FormUrlEncoded
                        )
                    }
                    post("/token") {
                        call.respondText(
                            "access_token=$accessToken&token_type=bearer",
                            ContentType.Application.FormUrlEncoded
                        )
                    }
                }
            }
        }
    }

    private suspend fun performOAuthFlow(
        client: HttpClient,
        loginPath: String = "/login",
        callbackPath: String = "/callback",
    ): HttpResponse {
        val authorizeResponse = client.get(loginPath)
        assertEquals(HttpStatusCode.OK, authorizeResponse.status)
        val params = parseQueryString(authorizeResponse.bodyAsText())
        assertEquals("test_code", params["code"])
        return client.get("$callbackPath?code=${params["code"]!!}&state=${params["state"]!!}")
    }

    @Test
    fun `oauth redirects to provider`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginPath = "/login"
            callback("/callback") { call.respondText("done") }
        }
        mockOAuthServices()

        routing {
            install(scheme)
        }

        val response = testClient.get("/callback")
        assertEquals(HttpStatusCode.OK, response.status)
        val params = parseQueryString(response.bodyAsText())
        assertEquals("test_code", params["code"])
    }

    @Test
    fun `oauth error invokes onUnauthorized`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginPath = "/login"
            onUnauthorized = { cause ->
                val message = (cause as? AuthenticationFailedCause.Error)?.message ?: cause.toString()
                call.respondText("forbidden:$message", status = HttpStatusCode.Forbidden)
            }
            callback("/callback") { call.respondText("done") }
        }

        routing {
            install(scheme)
        }

        val response = testClient.get("/callback?error=access_denied&error_description=denied")
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("forbidden:access_denied: denied", response.bodyAsText())
    }

    @Test
    fun `oauth callback creates session and protects routes`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val oauth = createOAuth(testClient)
        mockOAuthServices(accessToken = "my_token")

        routing {
            install(oauth)
            authenticateWith(oauth.session) {
                get("/protected") {
                    val p = call.principal
                    call.respondText("${call.session.accessToken}:${p.token}:${p.source}")
                }
            }
        }

        val authResponse = performOAuthFlow(testClient)
        assertEquals("my_token:my_token:session", authResponse.bodyAsText())

        val protectedResponse = testClient.get("/protected")
        assertEquals(HttpStatusCode.OK, protectedResponse.status)
        assertEquals("my_token:my_token:session", protectedResponse.bodyAsText())
    }

    @Test
    fun `missing session returns 401`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = createOAuth(testClient)
        mockOAuthServices()

        routing {
            install(scheme)
            authenticateWith(scheme.session) {
                get("/protected") { call.respondText(call.principal.token) }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, testClient.get("/protected").status)
    }

    @Test
    fun `application install delegates to routing install`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2(name = "test-oauth") {
            client = testClient
            loginPath = "/login"
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            callback("/callback") { call.respondText("installed") }
        }
        mockOAuthServices()

        application {
            install(scheme)
        }

        val response = testClient.get("/callback?code=test_code&state=test_state")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("installed", response.bodyAsText())
    }

    @Test
    fun `missing sessionCreator throws`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HttpClient().use { c ->
                oauth2Session<OAuthPrincipal, OAuthSession>("google") {
                    client = c
                    providerLookup = { null }
                    loginPath = "/login"
                    callback("/callback", onFailure = {}) { call.respondText("done") }
                    sessions { }
                }
            }
        }
        assertContains(
            failure.message.orEmpty(),
            "OAuth session flow 'google' requires sessionCreator in sessions { ... }"
        )
    }

    @Test
    fun `missing validate throws`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HttpClient().use { c ->
                oauth2Session<OAuthPrincipal, OAuthSession>(name = "google") {
                    client = c
                    loginPath = "/login"
                    providerLookup = { null }
                    callback("/callback", onFailure = {}) { call.respondText("done") }
                    sessions {
                        sessionCreator = { OAuthSession("token") }
                    }
                }
            }
        }
        assertContains(
            failure.message.orEmpty(),
            "OAuth session flow 'google' requires validate { ... } in sessions { ... }"
        )
    }

    @Test
    fun `missing callback throws`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HttpClient().use { c ->
                oauth2(name = "google") {
                    client = c
                    loginPath = "/login"
                    settings = OAuthServerSettings.OAuth2ServerSettings(
                        name = "test-provider",
                        authorizeUrl = "http://oauth.test/authorize",
                        accessTokenUrl = "http://oauth.test/token",
                        clientId = "test-client-id",
                        clientSecret = "test-client-secret",
                        requestMethod = HttpMethod.Post,
                    )
                }
            }
        }
        assertContains(failure.message.orEmpty(), "OAuth flow 'google' requires a callback route")
    }

    @Test
    fun `oauth form_post callback succeeds`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2(name = "test-oauth") {
            client = testClient
            loginPath = "/login"
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            callback("/callback") { call.respondText("form_post") }
        }
        mockOAuthServices()

        routing {
            install(scheme)
        }

        val authorizeResponse = testClient.get("/callback")
        assertEquals(HttpStatusCode.OK, authorizeResponse.status)
        val params = parseQueryString(authorizeResponse.bodyAsText())
        assertEquals("test_code", params["code"])

        val response = testClient.post("/callback") {
            header(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
            setBody(
                listOf(
                    OAuth2RequestParameters.Code to params["code"]!!,
                    OAuth2RequestParameters.State to params["state"]!!,
                ).formUrlEncode()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("form_post", response.bodyAsText())
    }

    @Test
    fun `basic auth token request omits client secret from body`() = testApplication {
        var tokenAuthHeader: String? = null
        var tokenBodyClientSecret: String? = null
        externalServices {
            hosts("http://oauth.test") {
                routing {
                    get("/authorize") {
                        val state = call.parameters["state"]!!
                        call.respondText("code=test_code&state=$state", ContentType.Application.FormUrlEncoded)
                    }
                    post("/token") {
                        tokenAuthHeader = call.request.headers[HttpHeaders.Authorization]
                        tokenBodyClientSecret = call.receiveParameters()["client_secret"]
                        call.respondText(
                            "access_token=test_token&token_type=bearer",
                            ContentType.Application.FormUrlEncoded
                        )
                    }
                }
            }
        }

        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
                accessTokenRequiresBasicAuth = true,
            )
            loginPath = "/login"
            callback("/callback") { call.respondText("done") }
        }

        routing {
            install(scheme)
        }

        val response = performOAuthFlow(testClient)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Basic " + "test-client-id:test-client-secret".encodeBase64(), tokenAuthHeader)
        assertNull(tokenBodyClientSecret, "client_secret must not be sent in the body when Basic auth is used")
    }

    @Test
    fun `oauth callback rotates session id planted before login`() = testApplication {
        val storage = SessionStorageMemory()
        val testClient = createClient { install(HttpCookies) }
        val oauth = oauth2Session<OAuthPrincipal, OAuthSession>("test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginPath = "/login"
            callback("/callback") { call.respondText("ok") }
            sessions {
                name = "user_session"
                transport = SessionTransportType.CookieId(storage)
                sessionCreator = { OAuthSession(it.accessToken) }
                validate { OAuthPrincipal(it.accessToken, source = "session") }
            }
        }
        mockOAuthServices()

        routing {
            install(oauth)
            authenticateWith(oauth.session) {
                get("/protected") { call.respondText(call.principal.token) }
            }
        }

        // The attacker completes a login of their own to get a session ID known to them.
        val attackerCallback = performOAuthFlow(testClient)
        assertEquals(HttpStatusCode.OK, attackerCallback.status)
        val plantedId = attackerCallback.setCookie().first { it.name == "user_session" }.value

        // The victim completes a login while the attacker's session ID is planted in their cookies.
        val victim = createClient {}
        val victimLogin = victim.get("/login")
        val params = parseQueryString(victimLogin.bodyAsText())
        val victimCallback = victim.get("/callback?code=${params["code"]!!}&state=${params["state"]!!}") {
            header(HttpHeaders.Cookie, "user_session=$plantedId")
        }
        assertEquals(HttpStatusCode.OK, victimCallback.status)
        val rotatedId = victimCallback.setCookie().first { it.name == "user_session" }.value

        assertNotEquals(plantedId, rotatedId, "authenticated session must not be stored under a pre-login session ID")
        assertFailsWith<NoSuchElementException>("planted session ID must be invalidated") { storage.read(plantedId) }

        val replay = victim.get("/protected") { header(HttpHeaders.Cookie, "user_session=$plantedId") }
        assertEquals(HttpStatusCode.Unauthorized, replay.status)

        val victimAccess = victim.get("/protected") { header(HttpHeaders.Cookie, "user_session=$rotatedId") }
        assertEquals(HttpStatusCode.OK, victimAccess.status)
    }

    @Test
    fun `oauth callback does not persist session when principal resolution fails`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2Session<OAuthPrincipal, OAuthSession>("test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginPath = "/login"
            callback("/callback", onFailure = { call.respondText("failed") }) {
                call.respondText("success")
            }
            sessions {
                transport = SessionTransportType.Cookie()
                sessionCreator = { OAuthSession(it.accessToken) }
                validate { null }
            }
        }
        mockOAuthServices()

        routing {
            install(scheme)
            authenticateWith(scheme.session) {
                get("/protected") { call.respondText(call.principal.token) }
            }
        }

        val authResponse = performOAuthFlow(testClient)
        assertEquals(HttpStatusCode.OK, authResponse.status)
        assertEquals("failed", authResponse.bodyAsText())

        assertEquals(HttpStatusCode.Unauthorized, testClient.get("/protected").status)
    }
}
