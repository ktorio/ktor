/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.tests.auth.typesafe

import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.testing.*
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Serializable
data class OAuthSession(val accessToken: String)

data class OAuthPrincipal(val token: String, val source: String)

class OAuthWithSessionTest {

    private fun createOAuthScheme(testClient: HttpClient): OAuthWithSessionScheme<OAuthSession, OAuthPrincipal> =
        oauthWithSession<OAuthSession, OAuthPrincipal>(
            name = "test-oauth",
            principalFactory = { OAuthPrincipal(it.accessToken, "session") }
        ) {
            oauth {
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
            }
            session {
                cookie<OAuthSession>("test_session")
            }
            sessionFactory { token ->
                val oauth2 = token as OAuthAccessTokenResponse.OAuth2
                OAuthSession(accessToken = oauth2.accessToken)
            }
        }

    private fun mockOAuthServices(builder: ApplicationTestBuilder, accessToken: String = "test_token") {
        builder.externalServices {
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
        mockOAuthServices(this)

        routing {
            oauthCallback(scheme, path = "/callback") { call.respondText("done") }
        }

        val response = testClient.get("/callback")
        assertEquals(HttpStatusCode.OK, response.status)
        val params = parseQueryString(response.bodyAsText())
        assertEquals("test_code", params["code"])
    }

    @Test
    fun `oauth callback creates session and protects routes`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = createOAuthScheme(testClient)
        mockOAuthServices(this, accessToken = "my_token")

        routing {
            oauthCallback(scheme, path = "/callback") {
                call.respondText("${session.accessToken}:${principal.token}:${principal.source}")
            }
            authenticateWith(scheme) {
                get("/protected") { call.respondText("${principal.token}:${principal.source}") }
            }
        }

        val authResponse = performOAuthFlow(testClient)
        assertEquals("my_token:my_token:session", authResponse.bodyAsText())

        val protectedResponse = testClient.get("/protected")
        assertEquals(HttpStatusCode.OK, protectedResponse.status)
        assertEquals("my_token:session", protectedResponse.bodyAsText())
    }

    @Test
    fun `missing session returns 401`() = testApplication {
        val testClient = createClient { install(HttpCookies) }
        val scheme = createOAuthScheme(testClient)
        mockOAuthServices(this)

        routing {
            oauthCallback(scheme, path = "/callback") { call.respondRedirect("/") }
            authenticateWith(scheme) {
                get("/protected") { call.respondText(principal.token) }
            }
        }

        assertEquals(HttpStatusCode.Unauthorized, testClient.get("/protected").status)
    }

    @Test
    fun `missing oauth config throws`() {
        assertFailsWith<IllegalArgumentException> {
            oauthWithSession<OAuthSession>("bad") {
                session { cookie<OAuthSession>("s") }
                sessionFactory { OAuthSession("t") }
            }
        }
    }

    @Test
    fun `missing session config throws`() {
        assertFailsWith<IllegalArgumentException> {
            oauthWithSession<OAuthSession>("bad") {
                oauth {
                    client = HttpClient()
                    urlProvider = { "" }
                    providerLookup = { null }
                }
                sessionFactory { OAuthSession("t") }
            }
        }
    }

    @Test
    fun `missing sessionFactory throws`() {
        assertFailsWith<IllegalArgumentException> {
            oauthWithSession<OAuthSession>("bad") {
                oauth {
                    client = HttpClient()
                    urlProvider = { "" }
                    providerLookup = { null }
                }
                session { cookie<OAuthSession>("s") }
            }
        }
    }
}
