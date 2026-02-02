/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.InternalAPI
import kotlin.jvm.JvmName

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth")

/**
 * An OAuth provider key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthKey)
 */
public val OAuthKey: Any = "OAuth"

/**
 * An `OAuth` [Authentication] provider.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider)
 *
 * @see [oauth]
 */
public class OAuthAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    internal val client: HttpClient = config.client

    internal val settings: OAuthServerSettings? = config.settings
    internal val providerLookup: (suspend ApplicationCall.() -> OAuthServerSettings?)? = config.providerLookup
    internal val urlProvider: suspend ApplicationCall.(OAuthServerSettings) -> String = config.urlProvider
    internal val fallback: suspend ApplicationCall.(AuthenticationFailedCause.Error) -> Unit = config.fallback

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        if (PlatformUtils.IS_JVM) oauth1a(name, context)
        oauth2(name, context)
    }

    /**
     * A configuration for the [oauth] authentication provider.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config)
     */
    public class Config internal constructor(
        name: String?,
        description: String?
    ) : AuthenticationProvider.Config(name, description) {
        /**
         * An HTTP client instance used to make requests to the OAuth server.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config.client)
         */
        public lateinit var client: HttpClient

        /**
         * A lookup function to find OAuth server settings for the particular call.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config.providerLookup)
         */
        public var providerLookup: (suspend ApplicationCall.() -> OAuthServerSettings?)? = null

        /**
         * Static OAuth server settings. Either this or [providerLookup] should be specified.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config.settings)
         */
        public var settings: OAuthServerSettings? = null

        /**
         * Specifies a redirect route opened when authorization is completed.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config.urlProvider)
         */
        public lateinit var urlProvider: suspend ApplicationCall.(OAuthServerSettings) -> String

        /**
         * Specifies a fallback function invoked when OAuth flow fails
         * with an [AuthenticationFailedCause.Error], e.g., a token exchange error, network/parse failure, etc.
         * If call is not handled in the fallback, `401 Unauthorized` will be responded.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuthAuthenticationProvider.Config.fallback)
         */
        public var fallback: suspend ApplicationCall.(AuthenticationFailedCause.Error) -> Unit = {}

        internal fun build(): OAuthAuthenticationProvider {
            require(settings != null || providerLookup != null) {
                "Either settings or providerLookup should be specified"
            }
            return OAuthAuthenticationProvider(this)
        }
    }
}

@InternalAPI
public fun OAuthAuthenticationProvider.staticSettings(): OAuthServerSettings? = settings

/**
 * Installs the OAuth [Authentication] provider.
 * OAuth can be used to authorize users of your application by using external providers,
 * such as Google, Facebook, Twitter, and so on.
 * To learn how to configure it, see [OAuth](https://ktor.io/docs/oauth.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oauth)
 */
public fun AuthenticationConfig.oauth(
    name: String? = null,
    configure: OAuthAuthenticationProvider.Config.() -> Unit
) {
    oauth(name, description = null, configure)
}

/**
 * Installs the OAuth [Authentication] provider with description.
 * OAuth can be used to authorize users of your application by using external providers,
 * such as Google, Facebook, Twitter, and so on.
 * To learn how to configure it, see [OAuth](https://ktor.io/docs/oauth.html).
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oauth)
 */
public fun AuthenticationConfig.oauth(
    name: String? = null,
    description: String? = null,
    configure: OAuthAuthenticationProvider.Config.() -> Unit
) {
    val provider = OAuthAuthenticationProvider.Config(name, description).apply(configure).build()
    register(provider)
}

/**
 * Installs the OAuth [Authentication] provider using OpenID Connect discovery configuration.
 *
 * This overload simplifies OAuth setup by using an [OpenIdConfiguration] obtained from
 * [HttpClient.fetchOpenIdConfiguration]. The following settings are autoconfigured from the discovery document:
 *
 * **Auto-configured from [OpenIdConfiguration]:**
 * - `authorizeUrl` - from [OpenIdConfiguration.authorizationEndpoint]
 * - `accessTokenUrl` - from [OpenIdConfiguration.tokenEndpoint]
 * - `defaultScopes` - from [OpenIdConfiguration.scopesSupported] (only "openid", "profile", "email" if available)
 *
 * **Must be configured manually:**
 * - [OpenIdOAuthConfig.clientId] - OAuth client ID from your identity provider
 * - [OpenIdOAuthConfig.clientSecret] - OAuth client secret from your identity provider
 * - [OpenIdOAuthConfig.urlProvider] - callback URL for OAuth redirect
 *
 * **Optional configuration:**
 * - [OpenIdOAuthConfig.client] - HTTP client for making OAuth requests
 * - [OpenIdOAuthConfig.defaultScopes] - override autoconfigured scopes
 * - [OpenIdOAuthConfig.requestMethod] - HTTP method for token requests (default: POST)
 * - [OpenIdOAuthConfig.extraAuthParameters] - additional parameters for authorization request
 * - [OpenIdOAuthConfig.extraTokenParameters] - additional parameters for token request
 * - [OpenIdOAuthConfig.onStateCreated] - callback when OAuth state is created
 * - [OpenIdOAuthConfig.fallback] - error handler for authentication failures
 *
 * Example:
 * ```kotlin
 * val openIdConfig = httpClient.fetchOpenIdConfiguration("https://accounts.google.com")
 * install(Authentication) {
 *     oauth("auth-oauth-google", openIdConfig) {
 *         clientId = "your-client-id"
 *         clientSecret = "your-client-secret"
 *         urlProvider = { "http://localhost:8080/callback" }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oauth)
 *
 * @param name optional provider name for use with [authenticate]
 * @param openIdConfiguration the OpenID Connect discovery configuration
 * @param configure configuration block for additional OAuth settings
 */
@JvmName("oauthWithOpenId")
public fun AuthenticationConfig.oauth(
    name: String? = null,
    openIdConfiguration: OpenIdConfiguration,
    configure: OpenIdOAuthConfig.() -> Unit
) {
    oauth(name, description = null, openIdConfiguration, configure)
}

/**
 * Installs the OAuth [Authentication] provider using OpenID Connect discovery configuration with description.
 * The same configuration as [AuthenticationConfig.oauth] but with an additional description parameter.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oauth)
 *
 * @see [AuthenticationConfig.oauth]
 */
@JvmName("oauthWithOpenId")
public fun AuthenticationConfig.oauth(
    name: String? = null,
    description: String? = null,
    openIdConfiguration: OpenIdConfiguration,
    configure: OpenIdOAuthConfig.() -> Unit
) {
    val openIdConfig = OpenIdOAuthConfig(openIdConfiguration).apply(configure)
    val urlProviderFn = requireNotNull(openIdConfig.urlProvider) {
        "urlProvider must be specified"
    }
    val httpClient = requireNotNull(openIdConfig.client) {
        "client must be specified"
    }
    oauth(name, description) {
        client = httpClient
        urlProvider = urlProviderFn
        fallback = openIdConfig.fallback
        settings = openIdConfig.toServerSettings()
    }
}

/**
 * Error container for when the upstream identity provider does not respond with the token credentials and instead
 * responds with error query parameters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuth2RedirectError)
 */
public class OAuth2RedirectError(public val error: String, public val errorDescription: String?) :
    AuthenticationFailedCause.Error(if (errorDescription == null) error else "$error: $errorDescription")

internal suspend fun OAuthAuthenticationProvider.oauth2(authProviderName: String?, context: AuthenticationContext) {
    val call = context.call
    val provider = providerLookup?.invoke(call) ?: settings
    if (provider !is OAuthServerSettings.OAuth2ServerSettings) return

    val callbackResponse = call.oauth2HandleCallback()
    val callbackRedirectUrl = call.urlProvider(provider)
    val cause: AuthenticationFailedCause? = when (callbackResponse) {
        is OAuthCallback.TokenSingle -> oauth2RequestToken(
            authProviderName,
            provider,
            callbackRedirectUrl,
            callbackResponse,
            context
        )

        is OAuthCallback.Error -> OAuth2RedirectError(callbackResponse.error, callbackResponse.errorDescription)
        else -> AuthenticationFailedCause.NoCredentials
    }

    cause ?: return

    if (cause is AuthenticationFailedCause.Error) {
        this@oauth2.fallback.invoke(call, cause)
        context.error(OAuthKey, cause)
        return
    }

    @Suppress("NAME_SHADOWING")
    context.challenge(OAuthKey, cause) { challenge, call ->
        val state = provider.nonceManager.newNonce()
        provider.onStateCreated(call, state)
        call.redirectAuthenticateOAuth2(
            provider,
            callbackRedirectUrl,
            state = state,
            scopes = provider.defaultScopes,
            extraParameters = provider.extraAuthParameters,
            interceptor = provider.authorizeUrlInterceptor
        )
        challenge.complete()
    }
}

internal expect suspend fun OAuthAuthenticationProvider.oauth1a(
    authProviderName: String?,
    context: AuthenticationContext
)

private suspend fun OAuthAuthenticationProvider.oauth2RequestToken(
    authProviderName: String?,
    provider: OAuthServerSettings.OAuth2ServerSettings,
    callbackRedirectUrl: String,
    token: OAuthCallback.TokenSingle,
    context: AuthenticationContext
) = try {
    val accessToken = oauth2RequestAccessToken(client, provider, callbackRedirectUrl, token)
    context.principal(authProviderName, accessToken)
    null
} catch (cause: OAuth2Exception.InvalidGrant) {
    Logger.trace("OAuth invalid grant reported: {}", cause)
    AuthenticationFailedCause.InvalidCredentials
} catch (cause: Throwable) {
    Logger.trace("OAuth2 request access token failed", cause)
    AuthenticationFailedCause.Error("Failed to request OAuth2 access token due to $cause")
}
