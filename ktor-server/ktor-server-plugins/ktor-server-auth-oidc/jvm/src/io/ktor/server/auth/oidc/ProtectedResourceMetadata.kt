/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 Protected Resource Metadata as defined in RFC 9728.
 *
 * Served at the `/.well-known/oauth-protected-resource` endpoint when
 * [OidcPluginConfig.protectedResource] is configured.
 *
 * @property resource The protected resource's identifier URL.
 * @property authorizationServers OAuth authorization server issuer identifiers trusted by this resource.
 * @property jwksUri URL of the resource server's JWK Set document containing its public keys.
 * @property scopesSupported OAuth 2.0 scope values that this resource server understands.
 * @property bearerMethodsSupported Methods supported for presenting Bearer tokens: `header`, `body`, `query`.
 * @property resourceSigningAlgValuesSupported JWS algorithms supported by this resource, excluding `none`.
 * @property resourceName Human-readable name of the protected resource.
 * @property resourceDocumentation URL of developer documentation for this resource.
 * @property resourcePolicyUri URL describing the resource's data usage requirements.
 * @property resourceTosUri URL of the resource's terms of service.
 * @property tlsClientCertificateBoundAccessTokens Whether this resource requires TLS client certificate-bound
 * access tokens.
 * @property authorizationDetailsTypesSupported Authorization details types supported per RFC 9396.
 * @property dpopSigningAlgValuesSupported JWS algorithms supported for DPoP proof validation.
 * @property dpopBoundAccessTokensRequired Whether DPoP-bound access tokens are required.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.ProtectedResourceMetadata)
 */
@Serializable
public class ProtectedResourceMetadata(
    public val resource: String,
    @SerialName("authorization_servers")
    public val authorizationServers: List<String>? = null,
    @SerialName("jwks_uri")
    public val jwksUri: String? = null,
    @SerialName("scopes_supported")
    public val scopesSupported: List<String>? = null,
    @SerialName("bearer_methods_supported")
    public val bearerMethodsSupported: List<String>? = null,
    @SerialName("resource_signing_alg_values_supported")
    public val resourceSigningAlgValuesSupported: List<String>? = null,
    @SerialName("resource_name")
    public val resourceName: String? = null,
    @SerialName("resource_documentation")
    public val resourceDocumentation: String? = null,
    @SerialName("resource_policy_uri")
    public val resourcePolicyUri: String? = null,
    @SerialName("resource_tos_uri")
    public val resourceTosUri: String? = null,
    @SerialName("tls_client_certificate_bound_access_tokens")
    public val tlsClientCertificateBoundAccessTokens: Boolean? = null,
    @SerialName("authorization_details_types_supported")
    public val authorizationDetailsTypesSupported: List<String>? = null,
    @SerialName("dpop_signing_alg_values_supported")
    public val dpopSigningAlgValuesSupported: List<String>? = null,
    @SerialName("dpop_bound_access_tokens_required")
    public val dpopBoundAccessTokensRequired: Boolean? = null,
)
