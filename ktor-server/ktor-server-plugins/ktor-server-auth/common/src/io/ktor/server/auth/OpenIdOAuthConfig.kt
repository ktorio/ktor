/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.HttpClient
import io.ktor.http.HttpMethod
import io.ktor.server.application.ApplicationCall
import io.ktor.util.GenerateOnlyNonceManager
import io.ktor.util.NonceManager
import io.ktor.utils.io.KtorDsl

/**
 * Configuration class for OAuth authentication using OpenID Connect discovery.
 *
 * This configuration is used with [AuthenticationConfig.oauth] when an [OpenIdConfiguration]
 * is provided. It pre-configures OAuth endpoints from the discovery document while allowing
 * customization of client credentials and other settings.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig)
 *
 * @property openIdConfiguration The OpenID Connect discovery configuration
 */
@KtorDsl
public class OpenIdOAuthConfig(
    private val openIdConfiguration: OpenIdConfiguration
) {
    /**
     * HTTP client used for OAuth requests.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.client)
     */
    public var client: HttpClient? = null

    /**
     * OAuth client ID obtained from your identity provider.
     * This is a required field.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.clientId)
     */
    public var clientId: String? = null

    /**
     * OAuth client secret obtained from your identity provider.
     * This is a required field.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.clientSecret)
     */
    public var clientSecret: String? = null

    /**
     * Provides the callback URL where the OAuth provider will redirect after authentication.
     * This is a required field.
     *
     * Example:
     * ```kotlin
     * urlProvider = { "http://localhost:8080/callback" }
     * ```
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.urlProvider)
     */
    public var urlProvider: (suspend ApplicationCall.(OAuthServerSettings) -> String)? = null

    /**
     * OAuth scopes to request during authorization.
     *
     * If not set, defaults to [OpenIdConfiguration.scopesSupported] or ["openid"] if not specified.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.defaultScopes)
     */
    public var defaultScopes: List<String>? = null

    /**
     * HTTP method to use when requesting access tokens.
     * Defaults to [HttpMethod.Post] which is required by most OAuth 2.0 providers.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.requestMethod)
     */
    public var requestMethod: HttpMethod = HttpMethod.Post

    /**
     * Whether to send client credentials using HTTP Basic authentication
     * when requesting access tokens.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.accessTokenRequiresBasicAuth)
     */
    public var accessTokenRequiresBasicAuth: Boolean = false

    /**
     * Additional parameters to include in the authorization request URL.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.extraAuthParameters)
     */
    public var extraAuthParameters: List<Pair<String, String>> = emptyList()

    /**
     * Additional parameters to include in the access token request.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.extraTokenParameters)
     */
    public var extraTokenParameters: List<Pair<String, String>> = emptyList()

    /**
     * Callback invoked when OAuth state is created, allowing you to store
     * additional data associated with the authentication flow.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.onStateCreated)
     */
    public var onStateCreated: suspend (call: ApplicationCall, state: String) -> Unit = { _, _ -> }

    /**
     * Fallback handler for authentication errors.
     * Called when OAuth flow fails with an error.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.fallback)
     */
    public var fallback: suspend ApplicationCall.(AuthenticationFailedCause.Error) -> Unit = {}

    /**
     * Nonce manager for generating and verifying state values.
     * Defaults to [GenerateOnlyNonceManager].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OpenIdOAuthConfig.nonceManager)
     */
    public var nonceManager: NonceManager = GenerateOnlyNonceManager

    internal fun toServerSettings(): OAuthServerSettings.OAuth2ServerSettings {
        val clientId = requireNotNull(clientId) { "clientId must be specified" }
        val clientSecret = requireNotNull(clientSecret) { "clientSecret must be specified" }

        val scopes = defaultScopes ?: openIdConfiguration.scopesSupported ?: listOf("openid")

        return OAuthServerSettings.OAuth2ServerSettings(
            name = "openid-${openIdConfiguration.issuer}",
            authorizeUrl = openIdConfiguration.authorizationEndpoint,
            accessTokenUrl = openIdConfiguration.tokenEndpoint,
            clientId = clientId,
            clientSecret = clientSecret,
            defaultScopes = scopes,
            requestMethod = requestMethod,
            accessTokenRequiresBasicAuth = accessTokenRequiresBasicAuth,
            extraAuthParameters = extraAuthParameters,
            extraTokenParameters = extraTokenParameters,
            onStateCreated = onStateCreated,
            nonceManager = nonceManager,
        )
    }
}
