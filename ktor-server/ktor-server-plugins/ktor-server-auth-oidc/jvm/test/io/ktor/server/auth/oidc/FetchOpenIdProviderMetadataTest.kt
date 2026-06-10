/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation

class FetchOpenIdProviderMetadataTest {

    private val discoveryJson = Json { ignoreUnknownKeys = true }

    private fun ApplicationTestBuilder.discoveryClient(): HttpClient = createClient {
        install(ClientContentNegotiation) {
            json(discoveryJson)
        }
    }

    private fun TestApplicationBuilder.configService(config: OpenIdProviderMetadata) {
        externalServices {
            hosts("http://localhost") {
                install(ContentNegotiation) {
                    json(discoveryJson)
                }
                routing {
                    get("/.well-known/openid-configuration") {
                        call.respond(config)
                    }
                }
            }
        }
    }

    private fun TestApplicationBuilder.errorConfigService(status: HttpStatusCode, body: String) {
        externalServices {
            hosts("http://localhost") {
                routing {
                    get("/.well-known/openid-configuration") {
                        call.respond(status, body)
                    }
                }
            }
        }
    }

    @Test
    fun `fetches OpenID configuration`() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
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

        val metadata = discoveryClient().fetchOpenIdMetadata(issuer)

        assertEquals(issuer, metadata.issuer)
        assertEquals("$issuer/authorize", metadata.authorizationEndpoint)
        assertEquals("$issuer/token", metadata.tokenEndpoint)
        assertEquals("$issuer/userinfo", metadata.userInfoEndpoint)
        assertEquals("$issuer/.well-known/jwks.json", metadata.jwksUri)
        assertEquals(listOf("openid", "profile", "email"), metadata.scopesSupported)
        assertEquals(listOf("code", "token"), metadata.responseTypesSupported)
        assertEquals(listOf("client_secret_basic"), metadata.tokenEndpointAuthMethodsSupported)
        assertEquals(listOf("sub", "name", "email"), metadata.claimsSupported)
    }

    @Test
    fun `fetches OpenID configuration new spec fields`() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json",
                endSessionEndpoint = "$issuer/logout",
                grantTypesSupported = listOf("authorization_code", "refresh_token"),
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf(
                    checkNotNull(SignatureAlgorithm.RSA_SHA_256.jwaName),
                    checkNotNull(SignatureAlgorithm.ECDSA_SHA_256.jwaName),
                ),
                responseModesSupported = listOf("query", "fragment"),
                claimsParameterSupported = true,
                requestParameterSupported = false,
                authorizationResponseIssParameterSupported = true,
            )
        )

        val config = discoveryClient().fetchOpenIdMetadata(issuer)

        assertEquals("$issuer/logout", config.endSessionEndpoint)
        assertEquals(listOf("authorization_code", "refresh_token"), config.grantTypesSupported)
        assertEquals(listOf("public"), config.subjectTypesSupported)
        assertEquals(
            listOf(
                checkNotNull(SignatureAlgorithm.RSA_SHA_256.jwaName),
                checkNotNull(SignatureAlgorithm.ECDSA_SHA_256.jwaName),
            ),
            config.idTokenSigningAlgValuesSupported,
        )
        assertEquals(listOf("query", "fragment"), config.responseModesSupported)
        assertEquals(true, config.claimsParameterSupported)
        assertEquals(false, config.requestParameterSupported)
        assertEquals(true, config.authorizationResponseIssParameterSupported)
        assertNull(config.registrationEndpoint)
        assertNull(config.checkSessionIframe)
    }

    @Test
    fun `fetches OpenID configuration with all optional fields null`() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json",
            )
        )

        val config = discoveryClient().fetchOpenIdMetadata(issuer)

        assertNull(config.userInfoEndpoint)
        assertNull(config.registrationEndpoint)
        assertNull(config.scopesSupported)
        assertNull(config.responseTypesSupported)
        assertNull(config.responseModesSupported)
        assertNull(config.grantTypesSupported)
        assertNull(config.acrValuesSupported)
        assertNull(config.subjectTypesSupported)
        assertNull(config.idTokenSigningAlgValuesSupported)
        assertNull(config.idTokenEncryptionAlgValuesSupported)
        assertNull(config.idTokenEncryptionEncValuesSupported)
        assertNull(config.userinfoSigningAlgValuesSupported)
        assertNull(config.userinfoEncryptionAlgValuesSupported)
        assertNull(config.userinfoEncryptionEncValuesSupported)
        assertNull(config.requestObjectSigningAlgValuesSupported)
        assertNull(config.requestObjectEncryptionAlgValuesSupported)
        assertNull(config.requestObjectEncryptionEncValuesSupported)
        assertNull(config.tokenEndpointAuthMethodsSupported)
        assertNull(config.tokenEndpointAuthSigningAlgValuesSupported)
        assertNull(config.displayValuesSupported)
        assertNull(config.claimTypesSupported)
        assertNull(config.claimsSupported)
        assertNull(config.claimsLocalesSupported)
        assertNull(config.uiLocalesSupported)
        assertNull(config.claimsParameterSupported)
        assertNull(config.requestParameterSupported)
        assertNull(config.requestUriParameterSupported)
        assertNull(config.requireRequestUriRegistration)
        assertNull(config.authorizationResponseIssParameterSupported)
        assertNull(config.opPolicyUri)
        assertNull(config.opTosUri)
        assertNull(config.endSessionEndpoint)
        assertNull(config.checkSessionIframe)
    }

    @Test
    fun `fetches OpenID configuration fails when jwks_uri is missing`() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = ""
            )
        )

        val exception = assertFailsWith<OpenIdDiscoveryException> {
            discoveryClient().fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing jwks_uri"))
    }

    @Test
    fun `fetches OpenID configuration fails when authorization_endpoint is missing`() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )

        val exception = assertFailsWith<OpenIdDiscoveryException> {
            discoveryClient().fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing authorization_endpoint"))
    }

    @Test
    fun `fetches OpenID configuration fails when token_endpoint is missing`() = testApplication {
        val issuer = "http://localhost"
        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )
        val exception = assertFailsWith<OpenIdDiscoveryException> {
            discoveryClient().fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing token_endpoint"))
    }

    @Test
    fun `fetches OpenID configuration fails on HTTP error`() = testApplication {
        val issuer = "http://localhost"

        errorConfigService(HttpStatusCode.NotFound, "Not Found")

        assertFailsWith<OpenIdDiscoveryException> {
            discoveryClient().fetchOpenIdMetadata(issuer)
        }
    }
}
