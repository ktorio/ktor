/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class OpenIdOAuthTest {

    private fun ApplicationTestBuilder.noRedirectsClient() = createClient { followRedirects = false }

    @Test
    fun testOAuthWithOpenIdConfiguration() = testApplication {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
            scopesSupported = listOf("openid", "profile", "email", "address"),
        )
        assertNull(openIdConfig.userInfoEndpoint)
        assertNull(openIdConfig.responseTypesSupported)
        assertNull(openIdConfig.tokenEndpointAuthMethodsSupported)
        assertNull(openIdConfig.claimsSupported)

        install(Authentication) {
            oauth("test-openid", openIdConfig) {
                client = this@testApplication.client
                clientId = "test-client-id"
                clientSecret = "test-client-secret"
                urlProvider = { "http://localhost/callback" }
            }
        }

        routing {
            authenticate("test-openid") {
                get("/login") {
                    call.respondText("Logged in")
                }
            }
        }

        val result = noRedirectsClient().get("/login")
        assertEquals(HttpStatusCode.Found, result.status)

        val location = result.headers[HttpHeaders.Location]!!
        val url = Url(location)

        assertEquals("test-issuer.example.com", url.host)
        assertEquals("/authorize", url.encodedPath)
        assertEquals("test-client-id", url.parameters["client_id"])
        assertEquals("code", url.parameters["response_type"])
        assertEquals("http://localhost/callback", url.parameters["redirect_uri"])

        val scopes = url.parameters["scope"]?.split(" ") ?: emptyList()
        assertTrue(scopes.contains("openid"))
        assertTrue(scopes.contains("profile"))
        assertTrue(scopes.contains("email"))
        assertTrue(scopes.contains("address"))
    }

    @Test
    fun testOAuthWithOpenIdConfigurationCustomScopes() = testApplication {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
            scopesSupported = listOf("openid", "profile", "email"),
        )

        install(Authentication) {
            oauth("test-openid", openIdConfig) {
                client = this@testApplication.client
                clientId = "test-client-id"
                clientSecret = "test-client-secret"
                urlProvider = { "http://localhost/callback" }
                defaultScopes = listOf("openid", "custom-scope")
            }
        }

        routing {
            authenticate("test-openid") {
                get("/login") {
                    call.respondText("Logged in")
                }
            }
        }

        val result = noRedirectsClient().get("/login")
        assertEquals(HttpStatusCode.Found, result.status)

        val location = result.headers[HttpHeaders.Location]!!
        val url = Url(location)

        val scopes = url.parameters["scope"]?.split(" ") ?: emptyList()
        assertTrue(scopes.contains("openid"))
        assertTrue(scopes.contains("custom-scope"))
        assertFalse(scopes.contains("profile"))
        assertFalse(scopes.contains("email"))
    }

    @Test
    fun testOAuthWithOpenIdConfigurationNoScopesSupported() = testApplication {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
            // scopesSupported is null
        )

        install(Authentication) {
            oauth("test-openid", openIdConfig) {
                client = this@testApplication.client
                clientId = "test-client-id"
                clientSecret = "test-client-secret"
                urlProvider = { "http://localhost/callback" }
            }
        }

        routing {
            authenticate("test-openid") {
                get("/login") {
                    call.respondText("Logged in")
                }
            }
        }

        val result = noRedirectsClient().get("/login")
        assertEquals(HttpStatusCode.Found, result.status)

        val location = result.headers[HttpHeaders.Location]!!
        val url = Url(location)

        // Should default to just "openid" when scopesSupported is null
        assertEquals("openid", url.parameters["scope"])
    }

    @Test
    fun testOAuthWithOpenIdConfigurationExtraParameters() = testApplication {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
        )

        install(Authentication) {
            oauth("test-openid", openIdConfig) {
                client = this@testApplication.client
                clientId = "test-client-id"
                clientSecret = "test-client-secret"
                urlProvider = { "http://localhost/callback" }
                extraAuthParameters = listOf("prompt" to "consent", "access_type" to "offline")
            }
        }

        routing {
            authenticate("test-openid") {
                get("/login") {
                    call.respondText("Logged in")
                }
            }
        }

        val result = noRedirectsClient().get("/login")
        assertEquals(HttpStatusCode.Found, result.status)

        val location = result.headers[HttpHeaders.Location]!!
        val url = Url(location)

        assertEquals("consent", url.parameters["prompt"])
        assertEquals("offline", url.parameters["access_type"])
    }

    @Test
    fun testOAuthWithOpenIdConfigurationMissingClientId() {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
        )

        assertFailsWith<IllegalArgumentException>("clientId must be specified") {
            testApplication {
                install(Authentication) {
                    oauth("test-openid", openIdConfig) {
                        client = this@testApplication.client
                        // clientId is not set
                        clientSecret = "test-client-secret"
                        urlProvider = { "http://localhost/callback" }
                    }
                }
            }
        }
    }

    @Test
    fun testOAuthWithOpenIdConfigurationMissingFields() {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "https://test-issuer.example.com/authorize",
            tokenEndpoint = "https://test-issuer.example.com/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
        )

        assertFailsWith<IllegalArgumentException>("urlProvider must be specified") {
            testApplication {
                install(Authentication) {
                    oauth("test-openid", openIdConfig) {
                        client = this@testApplication.client
                        clientId = "test-client-id"
                        clientSecret = "test-client-secret"
                        // urlProvider is not set
                    }
                }
            }
        }

        assertFailsWith<IllegalArgumentException>("clientSecret must be specified") {
            testApplication {
                install(Authentication) {
                    oauth("test-openid", openIdConfig) {
                        client = this@testApplication.client
                        clientId = "test-client-id"
                        // clientSecret is not set
                        urlProvider = { "http://localhost/callback" }
                    }
                }
            }
        }
    }

    @Test
    fun testOAuthWithOpenIdConfigurationTokenExchange() = testApplication {
        val openIdConfig = OpenIdConfiguration(
            issuer = "https://test-issuer.example.com",
            authorizationEndpoint = "http://localhost/authorize",
            tokenEndpoint = "http://localhost/token",
            jwksUri = "https://test-issuer.example.com/.well-known/jwks.json",
            scopesSupported = listOf("openid", "profile"),
        )

        install(Authentication) {
            oauth("test-openid", openIdConfig) {
                client = this@testApplication.client
                clientId = "test-client-id"
                clientSecret = "test-client-secret"
                urlProvider = { "http://localhost/callback" }
            }
        }

        routing {
            post("/token") {
                call.respondText(
                    "access_token=test-token&token_type=Bearer&expires_in=3600",
                    ContentType.Application.FormUrlEncoded
                )
            }
            authenticate("test-openid") {
                get("/callback") {
                    val principal = call.principal<OAuthAccessTokenResponse.OAuth2>()
                    call.respondText("Token: ${principal?.accessToken}")
                }
            }
        }

        val result = client.get("/callback?code=test-code&state=test-state")
        assertEquals(HttpStatusCode.OK, result.status)
    }
}
