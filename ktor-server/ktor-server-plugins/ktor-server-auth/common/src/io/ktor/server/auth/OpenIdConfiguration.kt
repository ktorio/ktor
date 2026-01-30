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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdConfiguration)
 *
 * @property issuer The authorization server's issuer identifier URL
 * @property jwksUri URL of the JSON Web Key Set document containing the server's public keys
 */
@Serializable
public data class OpenIdConfiguration(
    val issuer: String,
    @SerialName("jwks_uri")
    val jwksUri: String
)

private val discoveryJson = Json {
    ignoreUnknownKeys = true
}

/**
 * Fetches OpenID Connect discovery document from the authorization server.
 * This extension function queries the `/.well-known/openid-configuration` endpoint to retrieve
 * server metadata including the JWKS URI.
 *
 * It is not required to apply `ContentNegotiation` plugin to the client.
 * JSON deserialization will be handled automatically.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.fetchOpenIdConfiguration)
 *
 * @param issuer The issuer URL (e.g., "https://accounts.google.com")
 * @throws DiscoveryException if the request fails or the response is invalid
 *
 * Example:
 * ```kotlin
 * val httpClient = HttpClient()
 * val config = httpClient.fetchOpenIdConfiguration("https://accounts.google.com")
 * println("JWKS URI: ${config.jwksUri}")
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
    return config
}

/**
 * Base exception for OpenID Connect discovery failures.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.DiscoveryException)
 *
 */
public class DiscoveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
