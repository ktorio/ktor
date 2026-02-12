/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.auth

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FetchOpenIdConfigurationTest {

    private val discoveryJson = Json { ignoreUnknownKeys = true }

    private fun TestApplicationBuilder.configService(config: OpenIdConfiguration) {
        externalServices {
            hosts("http://localhost") {
                routing {
                    get("/.well-known/openid-configuration") {
                        val text = discoveryJson.encodeToString(config)
                        call.respondText(text, ContentType.Application.Json)
                    }
                }
            }
        }
    }

    @Test
    fun testFetchOpenIdConfiguration() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdConfiguration(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                userInfoEndpoint = "$issuer/userinfo",
                jwksUri = "$issuer/.well-known/jwks.json",
                scopesSupported = listOf("openid", "profile", "email"),
                responseTypesSupported = listOf("code", "token"),
                tokenEndpointAuthMethodsSupported = listOf("client_secret_basic"),
                claimsSupported = listOf("sub", "name", "email")
            )
        )

        val config = client.fetchOpenIdConfiguration(issuer)

        assertEquals(issuer, config.issuer)
        assertEquals("$issuer/authorize", config.authorizationEndpoint)
        assertEquals("$issuer/token", config.tokenEndpoint)
        assertEquals("$issuer/userinfo", config.userInfoEndpoint)
        assertEquals("$issuer/.well-known/jwks.json", config.jwksUri)
        assertEquals(listOf("openid", "profile", "email"), config.scopesSupported)
        assertEquals(listOf("code", "token"), config.responseTypesSupported)
        assertEquals(listOf("client_secret_basic"), config.tokenEndpointAuthMethodsSupported)
        assertEquals(listOf("sub", "name", "email"), config.claimsSupported)
    }

    @Test
    fun testFetchOpenIdConfigurationMissingJwksUri() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdConfiguration(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = ""
            )
        )

        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdConfiguration(issuer)
        }
        assertTrue(exception.message!!.contains("missing jwks_uri"))
    }

    @Test
    fun testFetchOpenIdConfigurationMissingAuthorizationEndpoint() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdConfiguration(
                issuer = issuer,
                authorizationEndpoint = "",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )

        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdConfiguration(issuer)
        }
        assertTrue(exception.message!!.contains("missing authorization_endpoint"))
    }

    @Test
    fun testFetchOpenIdConfigurationMissingTokenEndpoint() = testApplication {
        val issuer = "http://localhost"
        configService(
            OpenIdConfiguration(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )
        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdConfiguration(issuer)
        }
        assertTrue(exception.message!!.contains("missing token_endpoint"))
    }

    @Test
    fun testFetchOpenIdConfigurationHttpError() = testApplication {
        val issuer = "http://localhost"

        externalServices {
            hosts("http://localhost") {
                routing {
                    get("/.well-known/openid-configuration") {
                        call.respond(HttpStatusCode.NotFound, "Not Found")
                    }
                }
            }
        }

        assertFailsWith<DiscoveryException> {
            client.fetchOpenIdConfiguration(issuer)
        }
    }
}
