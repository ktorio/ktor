/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
data class OAuthSession(val accessToken: String)

data class OAuthPrincipal(val token: String, val source: String)

class OAuthFlowTest {

    private fun createOAuthScheme(testClient: HttpClient) =
        oauth2SessionFlow<OAuthPrincipal, OAuthSession>("test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            callback("/callback") {
                val user = call.principal
                call.respondText(call.session.accessToken + ":" + user.token + ":" + user.source)
            }
            sessions {
                transport = SessionTransport.Cookie()
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
        loginPath: String = "/callback",
        callbackPath: String = loginPath,
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
        val scheme = oauth2Flow(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
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
        val scheme = oauth2Flow(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
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
        val scheme = createOAuthScheme(testClient)
        mockOAuthServices(accessToken = "my_token")

        routing {
            install(scheme)
            authenticateWith(scheme.sessions) {
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
        val scheme = createOAuthScheme(testClient)
        mockOAuthServices()

        routing {
            install(scheme)
            authenticateWith(scheme.sessions) {
                get("/protected") { call.respondText(call.principal.token) }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, testClient.get("/protected").status)
    }

    @Test
    fun `loginUri completes oauth flow`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2Flow(name = "test-oauth") {
            client = testClient
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            loginUri = { path("custom", "login") }
            callback("/callback") { call.respondText("done") }
        }
        mockOAuthServices()

        routing {
            install(scheme)
        }

        val response = performOAuthFlow(
            client = testClient,
            loginPath = "/custom/login",
            callbackPath = "/callback",
        )
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("done", response.bodyAsText())
    }

    @Test
    fun `application install delegates to routing install`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2Flow(name = "test-oauth") {
            client = testClient
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
                oauth2SessionFlow<OAuthPrincipal, OAuthSession>("google") {
                    client = c
                    providerLookup = { null }
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
                oauth2SessionFlow<OAuthPrincipal, OAuthSession>(name = "google") {
                    client = c
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
                oauth2Flow(name = "google") {
                    client = c
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
}
