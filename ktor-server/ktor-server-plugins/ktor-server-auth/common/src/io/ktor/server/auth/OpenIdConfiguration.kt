/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenID Connect discovery document containing OAuth 2.0 authorization server metadata.
 *
 * This class represents the metadata returned from the `/.well-known/openid-configuration` endpoint
 * as defined in the [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
 * specification.
 *
 * Use [HttpClient.fetchOpenIdConfiguration] to fetch this configuration from an identity provider,
 * then pass it to [AuthenticationConfig.oauth] to automatically configure OAuth authentication.
 *
 * Example:
 * ```kotlin
 * val openIdConfig = httpClient.fetchOpenIdConfiguration("https://accounts.google.com")
 * install(Authentication) {
 *     oauth("auth-oauth-openid", openIdConfig) {
 *         clientId = "your-client-id"
 *         clientSecret = "your-client-secret"
 *         urlProvider = { "http://localhost:8080/callback" }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdConfiguration)
 *
 * @property issuer The authorization server's issuer identifier URL
 * @property authorizationEndpoint URL of the authorization endpoint for user authentication
 * @property tokenEndpoint URL of the token endpoint for obtaining access tokens
 * @property userInfoEndpoint URL of the userinfo endpoint for retrieving user claims (optional)
 * @property jwksUri URL of the JSON Web Key Set document containing the server's public keys
 * @property scopesSupported List of OAuth 2.0 scope values supported by the server (optional)
 * @property responseTypesSupported List of OAuth 2.0 response types supported by the server (optional)
 * @property tokenEndpointAuthMethodsSupported List of client authentication methods supported
 *           at the token endpoint (optional)
 * @property claimsSupported List of claim names supported by the server (optional)
 */
@Serializable
public class OpenIdConfiguration(
    public val issuer: String,
    @SerialName("authorization_endpoint")
    public val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    public val tokenEndpoint: String,
    @SerialName("userinfo_endpoint")
    public val userInfoEndpoint: String? = null,
    @SerialName("jwks_uri")
    public val jwksUri: String,
    @SerialName("scopes_supported")
    public val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported")
    public val responseTypesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    public val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("claims_supported")
    public val claimsSupported: List<String>? = null,
)

private val discoveryJson = Json {
    ignoreUnknownKeys = true
}

/**
 * Fetches OpenID Connect discovery document from the authorization server.
 *
 * This extension function queries the `/.well-known/openid-configuration` endpoint to retrieve
 * server metadata including authorization endpoints, token endpoints, and JWKS URI.
 *
 * It is not required to apply `ContentNegotiation` plugin to the client.
 * JSON deserialization will be handled automatically.
 *
 * The returned [OpenIdConfiguration] can be passed to [AuthenticationConfig.oauth] to automatically
 * configure OAuth authentication with the discovered endpoints.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.fetchOpenIdConfiguration)
 *
 * @param issuer The issuer URL (e.g., "https://accounts.google.com")
 * @return The OpenID Connect configuration containing endpoints and metadata
 * @throws DiscoveryException if the request fails or the response is invalid
 *
 * Example:
 * ```kotlin
 * val httpClient = HttpClient()
 * val config = httpClient.fetchOpenIdConfiguration("https://accounts.google.com")
 *
 * install(Authentication) {
 *     oauth("auth-oauth-openid", config) {
 *         clientId = "your-client-id"
 *         clientSecret = "your-client-secret"
 *         urlProvider = { "http://localhost:8080/callback" }
 *     }
 * }
 * ```
 */
public suspend fun HttpClient.fetchOpenIdConfiguration(issuer: String): OpenIdConfiguration {
    val config = runCatching {
        val response = get {
            expectSuccess = true
            url.takeFrom(issuer)
            url.appendPathSegments(".well-known", "openid-configuration")
        }
        val body = response.bodyAsText()
        discoveryJson.decodeFromString<OpenIdConfiguration>(body)
    }.getOrElse {
        throw DiscoveryException("Failed to fetch OpenID configuration from $issuer", it)
    }
    if (config.jwksUri.isBlank()) {
        throw DiscoveryException("OpenID configuration from $issuer is missing jwks_uri")
    }
    if (config.authorizationEndpoint.isBlank()) {
        throw DiscoveryException("OpenID configuration from $issuer is missing authorization_endpoint")
    }
    if (config.tokenEndpoint.isBlank()) {
        throw DiscoveryException("OpenID configuration from $issuer is missing token_endpoint")
    }
    return config
}

/**
 * Base exception for OpenID Connect discovery failures.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DiscoveryException)
 *
 */
public class DiscoveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
