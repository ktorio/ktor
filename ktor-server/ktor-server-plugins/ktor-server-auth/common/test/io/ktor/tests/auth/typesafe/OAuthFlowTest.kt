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
        oauth2Flow(name = "test-oauth") {
            client = testClient
            urlProvider = { "http://localhost/callback" }
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
        }.withSessions<OAuthPrincipal, OAuthSession> {
            storage {
                cookie(scheme = it)
            }
            sessionCreator = { token ->
                val oauth2 = token as OAuthAccessTokenResponse.OAuth2
                OAuthSession(accessToken = oauth2.accessToken)
            }
            principalResolver = { OAuthPrincipal(it.accessToken, source = "session") }
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

    private suspend fun performOAuthFlow(client: HttpClient, path: String = "/callback"): HttpResponse {
        val authorizeResponse = client.get(path)
        val params = parseQueryString(authorizeResponse.bodyAsText())
        return client.get("$path?code=${params["code"]!!}&state=${params["state"]!!}")
    }

    @Test
    fun `oauth redirects to provider`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = createOAuthScheme(testClient)
        mockOAuthServices()

        routing {
            oauthCallback(scheme, path = "/callback") { call.respondText("done") }
        }

        val response = testClient.get("/callback")
        assertEquals(HttpStatusCode.OK, response.status)
        val params = parseQueryString(response.bodyAsText())
        assertEquals("test_code", params["code"])
    }

    @Test
    fun `oauth error invokes onForbidden`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = oauth2Flow(name = "test-oauth") {
            client = testClient
            urlProvider = { "http://localhost/callback" }
            settings = OAuthServerSettings.OAuth2ServerSettings(
                name = "test-provider",
                authorizeUrl = "http://oauth.test/authorize",
                accessTokenUrl = "http://oauth.test/token",
                clientId = "test-client-id",
                clientSecret = "test-client-secret",
                requestMethod = HttpMethod.Post,
            )
            onForbidden = { cause ->
                val message = (cause as? AuthenticationFailedCause.Error)?.message ?: cause.toString()
                call.respondText("forbidden:$message", status = HttpStatusCode.Forbidden)
            }
        }

        routing {
            oauthCallback(scheme, path = "/callback") { call.respondText("done") }
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
            oauthCallback(scheme, path = "/callback") {
                call.respondText("${session.accessToken}:${principal.token}:${principal.source}")
            }
            authenticateWith(scheme.sessions) {
                get("/protected") { call.respondText("${session.accessToken}:${principal.token}:${principal.source}") }
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
            oauthCallback(scheme, path = "/callback") { call.respondRedirect("/") }
            authenticateWith(scheme.sessions) {
                get("/protected") { call.respondText(principal.token) }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, testClient.get("/protected").status)
    }

    @Test
    fun `missing sessionFactory throws`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HttpClient().use { c ->
                oauth2Flow(name = "google") {
                    client = c
                    urlProvider = { "" }
                    providerLookup = { null }
                }.withSessions<OAuthPrincipal, OAuthSession> {}
            }
        }
        assertContains(failure.message.orEmpty(), "Session creator cannot be null for OAuth2SessionFlow google")
    }

    @Test
    fun `missing principal resolver throws`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            HttpClient().use { c ->
                oauth2Flow(name = "google") {
                    client = c
                    urlProvider = { "" }
                    providerLookup = { null }
                }.withSessions<OAuthPrincipal, OAuthSession> {
                    sessionCreator = { OAuthSession("token") }
                }
            }
        }
        assertContains(failure.message.orEmpty(), "Principal resolver cannot be null for OAuth2SessionFlow google")
    }
}
