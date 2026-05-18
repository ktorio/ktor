/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetchOpenIdProviderMetadataTest {

    private val discoveryJson = Json { ignoreUnknownKeys = true }

    private fun TestApplicationBuilder.configService(config: OpenIdProviderMetadata) {
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
    fun testFetchOpenIdConfiguration() = testApplication {
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

        val metadata = client.fetchOpenIdMetadata(issuer)

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
    fun testFetchOpenIdConfigurationNewSpecFields() = testApplication {
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

        val config = client.fetchOpenIdMetadata(issuer)

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
    fun testFetchOpenIdConfigurationAllOptionalFieldsNull() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json",
            )
        )

        val config = client.fetchOpenIdMetadata(issuer)

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
    fun testFetchOpenIdConfigurationMissingJwksUri() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "$issuer/token",
                jwksUri = ""
            )
        )

        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing jwks_uri"))
    }

    @Test
    fun testFetchOpenIdConfigurationMissingAuthorizationEndpoint() = testApplication {
        val issuer = "http://localhost"

        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "",
                tokenEndpoint = "$issuer/token",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )

        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing authorization_endpoint"))
    }

    @Test
    fun testFetchOpenIdConfigurationMissingTokenEndpoint() = testApplication {
        val issuer = "http://localhost"
        configService(
            OpenIdProviderMetadata(
                issuer = issuer,
                authorizationEndpoint = "$issuer/authorize",
                tokenEndpoint = "",
                jwksUri = "$issuer/.well-known/jwks.json"
            )
        )
        val exception = assertFailsWith<DiscoveryException> {
            client.fetchOpenIdMetadata(issuer)
        }
        assertTrue(exception.message!!.contains("missing token_endpoint"))
    }

    @Test
    fun testFetchOpenIdConfigurationHttpError() = testApplication {
        val issuer = "http://localhost"

        errorConfigService(HttpStatusCode.NotFound, "Not Found")

        assertFailsWith<DiscoveryException> {
            client.fetchOpenIdMetadata(issuer)
        }
    }
}
