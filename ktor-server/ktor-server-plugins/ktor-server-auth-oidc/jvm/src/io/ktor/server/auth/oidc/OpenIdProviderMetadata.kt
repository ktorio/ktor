/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenID Connect discovery document containing OAuth 2.0 authorization server metadata.
 *
 * This class represents the metadata returned from the `/.well-known/openid-configuration` endpoint
 * as defined in the [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata)
 * specification.
 *
 * Use [HttpClient.fetchOpenIdMetadata] to fetch this configuration from an identity provider.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OpenIdProviderMetadata)
 *
 * @property issuer The authorization server's issuer identifier URL.
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property authorizationEndpoint URL of the authorization endpoint for user authentication.
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property tokenEndpoint URL of the token endpoint for obtaining access tokens.
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property userInfoEndpoint URL of the userinfo endpoint for retrieving user claims (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property jwksUri URL of the JSON Web Key Set document containing the server's public keys.
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property registrationEndpoint URL of the dynamic client registration endpoint (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property scopesSupported List of OAuth 2.0 scope values supported by the server (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property responseTypesSupported List of OAuth 2.0 response types supported by the server (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property responseModesSupported List of OAuth 2.0 response mode values supported,
 *   e.g. `query`, `fragment`, `form_post` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property grantTypesSupported List of OAuth 2.0 grant type values supported,
 *   e.g. `authorization_code`, `implicit` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property acrValuesSupported List of Authentication Context Class Reference values supported (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property subjectTypesSupported List of Subject Identifier types supported,
 *   e.g. `pairwise`, `public` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property idTokenSigningAlgValuesSupported List of JWS signing algorithm values supported for ID Tokens (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property idTokenEncryptionAlgValuesSupported List of JWE encryption algorithm values supported for ID Tokens
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property idTokenEncryptionEncValuesSupported List of JWE encryption encoding values supported for ID Tokens
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property userinfoSigningAlgValuesSupported List of JWS signing algorithm values supported for the UserInfo
 *   endpoint response (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property userinfoEncryptionAlgValuesSupported List of JWE encryption algorithm values supported for the UserInfo
 *   endpoint response (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property userinfoEncryptionEncValuesSupported List of JWE encryption encoding values supported for the UserInfo
 *   endpoint response (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requestObjectSigningAlgValuesSupported List of JWS signing algorithm values supported for Request Objects
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requestObjectEncryptionAlgValuesSupported List of JWE encryption algorithm values supported for Request
 *   Objects (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requestObjectEncryptionEncValuesSupported List of JWE encryption encoding values supported for Request
 *   Objects (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property tokenEndpointAuthMethodsSupported List of client authentication methods supported at the token endpoint
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property tokenEndpointAuthSigningAlgValuesSupported List of JWS signing algorithm values supported at the token
 *   endpoint for `private_key_jwt` and `client_secret_jwt` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property displayValuesSupported List of `display` parameter values supported,
 *   e.g. `page`, `popup`, `touch`, `wap` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property claimTypesSupported List of claim types supported, e.g. `normal`, `aggregated`, `distributed` (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property claimsSupported List of claim names supported by the server (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property claimsLocalesSupported Languages and scripts supported for values in claims as BCP 47 language tags
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property uiLocalesSupported Languages and scripts supported for the user interface as BCP 47 language tags
 *   (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property claimsParameterSupported Whether the `claims` request parameter is supported (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requestParameterSupported Whether the `request` request parameter is supported (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requestUriParameterSupported Whether the `request_uri` request parameter is supported (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property requireRequestUriRegistration Whether `request_uri` values must be pre-registered (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property authorizationResponseIssParameterSupported Whether authorization responses include the RFC 9207 `iss`
 *   parameter (optional).
 * @property opPolicyUri URL of the OP's data usage policies document (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property opTosUri URL of the OP's terms of service document (optional).
 *   See [OpenID Connect Discovery 1.0 §3](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata).
 * @property endSessionEndpoint URL of the end session endpoint for RP-Initiated Logout (optional).
 *   See [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html#OPMetadata).
 * @property checkSessionIframe URL of the OP iframe for session management (optional).
 *   See [OpenID Connect Session Management 1.0](https://openid.net/specs/openid-connect-session-1_0.html#OPMetadata).
 */
@Serializable
public class OpenIdProviderMetadata(
    public val issuer: String,
    @SerialName("authorization_endpoint")
    public val authorizationEndpoint: String,
    @SerialName("token_endpoint")
    public val tokenEndpoint: String,
    @SerialName("userinfo_endpoint")
    public val userInfoEndpoint: String? = null,
    @SerialName("jwks_uri")
    public val jwksUri: String,
    @SerialName("registration_endpoint")
    public val registrationEndpoint: String? = null,
    @SerialName("scopes_supported")
    public val scopesSupported: List<String>? = null,
    @SerialName("response_types_supported")
    public val responseTypesSupported: List<String>? = null,
    @SerialName("response_modes_supported")
    public val responseModesSupported: List<String>? = null,
    @SerialName("grant_types_supported")
    public val grantTypesSupported: List<String>? = null,
    @SerialName("acr_values_supported")
    public val acrValuesSupported: List<String>? = null,
    @SerialName("subject_types_supported")
    public val subjectTypesSupported: List<String>? = null,
    @SerialName("id_token_signing_alg_values_supported")
    public val idTokenSigningAlgValuesSupported: List<String>? = null,
    @SerialName("id_token_encryption_alg_values_supported")
    public val idTokenEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("id_token_encryption_enc_values_supported")
    public val idTokenEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("userinfo_signing_alg_values_supported")
    public val userinfoSigningAlgValuesSupported: List<String>? = null,
    @SerialName("userinfo_encryption_alg_values_supported")
    public val userinfoEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("userinfo_encryption_enc_values_supported")
    public val userinfoEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("request_object_signing_alg_values_supported")
    public val requestObjectSigningAlgValuesSupported: List<String>? = null,
    @SerialName("request_object_encryption_alg_values_supported")
    public val requestObjectEncryptionAlgValuesSupported: List<String>? = null,
    @SerialName("request_object_encryption_enc_values_supported")
    public val requestObjectEncryptionEncValuesSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_methods_supported")
    public val tokenEndpointAuthMethodsSupported: List<String>? = null,
    @SerialName("token_endpoint_auth_signing_alg_values_supported")
    public val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,
    @SerialName("display_values_supported")
    public val displayValuesSupported: List<String>? = null,
    @SerialName("claim_types_supported")
    public val claimTypesSupported: List<String>? = null,
    @SerialName("claims_supported")
    public val claimsSupported: List<String>? = null,
    @SerialName("claims_locales_supported")
    public val claimsLocalesSupported: List<String>? = null,
    @SerialName("ui_locales_supported")
    public val uiLocalesSupported: List<String>? = null,
    @SerialName("claims_parameter_supported")
    public val claimsParameterSupported: Boolean? = null,
    @SerialName("request_parameter_supported")
    public val requestParameterSupported: Boolean? = null,
    @SerialName("request_uri_parameter_supported")
    public val requestUriParameterSupported: Boolean? = null,
    @SerialName("require_request_uri_registration")
    public val requireRequestUriRegistration: Boolean? = null,
    @SerialName("authorization_response_iss_parameter_supported")
    public val authorizationResponseIssParameterSupported: Boolean? = null,
    @SerialName("op_policy_uri")
    public val opPolicyUri: String? = null,
    @SerialName("op_tos_uri")
    public val opTosUri: String? = null,
    @SerialName("end_session_endpoint")
    public val endSessionEndpoint: String? = null,
    @SerialName("check_session_iframe")
    public val checkSessionIframe: String? = null,
)

/**
 * Fetches OpenID Connect discovery document from the authorization server.
 *
 * This extension function queries the `/.well-known/openid-configuration` endpoint to retrieve
 * server metadata including authorization endpoints, token endpoints, and JWKS URI.
 *
 * The client must have [ContentNegotiation][io.ktor.client.plugins.contentnegotiation.ContentNegotiation]
 * installed with a JSON converter that can deserialize [OpenIdProviderMetadata].
 *
 * The returned [OpenIdProviderMetadata] contains discovered endpoint URLs and supported features.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.fetchOpenIdMetadata)
 *
 * @param issuer The issuer URL (e.g., "https://accounts.google.com")
 * @return The OpenID Connect configuration containing endpoints and metadata
 * @throws OpenIdDiscoveryException if the request fails or the response is invalid
 *
 */
public suspend fun HttpClient.fetchOpenIdMetadata(issuer: String): OpenIdProviderMetadata {
    val config = try {
        get {
            expectSuccess = true
            url.takeFrom(issuer)
            url.appendPathSegments(".well-known", "openid-configuration")
        }.body<OpenIdProviderMetadata>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw OpenIdDiscoveryException("Failed to fetch OpenID configuration from $issuer", e)
    }
    if (config.jwksUri.isBlank()) {
        throw OpenIdDiscoveryException("OpenID configuration from $issuer is missing jwks_uri")
    }
    if (config.authorizationEndpoint.isBlank()) {
        throw OpenIdDiscoveryException("OpenID configuration from $issuer is missing authorization_endpoint")
    }
    if (config.tokenEndpoint.isBlank()) {
        throw OpenIdDiscoveryException("OpenID configuration from $issuer is missing token_endpoint")
    }
    return config
}

/**
 * Base exception for OpenID Connect discovery failures.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.DiscoveryException)
 *
 */
public class OpenIdDiscoveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
