/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.utils.io.*

/**
 * Configuration for OAuth 2.0 Protected Resource Metadata (RFC 9728).
 *
 * Fields that can be auto-derived from provider configuration, such as [authorizationServers],
 * [scopesSupported], and [bearerMethodsSupported], are populated automatically unless explicitly overridden.
 *
 * @param resource The protected resource's identifier URL. It must be an HTTPS URL with a host,
 * no userinfo, no query, and no fragment.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.ProtectedResourceMetadataConfig)
 */
@KtorDsl
public class ProtectedResourceMetadataConfig internal constructor(
    public val resource: String,
) {
    /**
     * OAuth authorization server issuer identifiers trusted by this resource.
     *
     * When `null`, auto-derived from all configured provider issuers.
     */
    public var authorizationServers: List<String>? = null

    /**
     * URL of the resource server's JWK Set document.
     *
     * When `null`, omitted from the metadata response.
     */
    public var jwksUri: String? = null

    /**
     * OAuth 2.0 scope values that this resource server understands.
     *
     * When `null`, auto-derived from the union of all provider OAuth scopes.
     */
    public var scopesSupported: List<String>? = null

    /**
     * Methods supported for presenting Bearer tokens: `header`, `body`, `query`.
     *
     * When `null`, auto-derived as `header` when at least one provider uses the default
     * `Authorization: Bearer` header extractor. Custom token extractors cannot be inferred; set this value
     * explicitly when a custom extractor reads tokens from another RFC 6750 location.
     */
    public var bearerMethodsSupported: List<String>? = null

    /**
     * JWS algorithms supported by this resource server, excluding `none`.
     *
     * When `null`, omitted from the metadata response.
     */
    public var resourceSigningAlgValuesSupported: List<String>? = null

    /**
     * Human-readable name of the protected resource.
     */
    public var resourceName: String? = null

    /**
     * URL of developer documentation for this resource.
     */
    public var resourceDocumentation: String? = null

    /**
     * URL describing the resource's data usage requirements.
     */
    public var resourcePolicyUri: String? = null

    /**
     * URL of the resource's terms of service.
     */
    public var resourceTosUri: String? = null

    /**
     * Whether this resource requires TLS client certificate-bound access tokens.
     */
    public var tlsClientCertificateBoundAccessTokens: Boolean? = null

    /**
     * Authorization details types supported per RFC 9396.
     */
    public var authorizationDetailsTypesSupported: List<String>? = null

    /**
     * JWS algorithms supported for DPoP proof validation.
     */
    public var dpopSigningAlgValuesSupported: List<String>? = null

    /**
     * Whether this resource requires DPoP-bound access tokens.
     */
    public var dpopBoundAccessTokensRequired: Boolean? = null
}
