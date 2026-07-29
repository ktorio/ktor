/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.server.application.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException

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
         * with an [AuthenticationFailedCause.Error], e.g., a token exchange error, network/parse failure, or `invalid_grant` error.
         *
         * If `invalid_grant` occurs, this fallback is invoked. If the fallback handles the call (e.g., by responding),
         * Ktor completes the authentication flow. If the fallback does not handle the call, Ktor falls back to
         * the redirect challenge to try to automatically recover the authentication flow.
         *
         * For other errors, or if the fallback handles the call, Ktor will not execute the redirect challenge and
         * will respond with `401 Unauthorized` if the call remains unhandled by the fallback.
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
 * Error container for when the upstream identity provider does not respond with the token credentials and instead
 * responds with error query parameters.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuth2RedirectError)
 */
public class OAuth2RedirectError(public val error: String, public val errorDescription: String?) :
    AuthenticationFailedCause.Error(if (errorDescription == null) error else "$error: $errorDescription")

/**
 * Error container for when the token endpoint responds with an `invalid_grant` error.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.OAuth2InvalidGrantError)
 *
 * @property cause the original [OAuth2Exception.InvalidGrant] exception thrown during token request.
 */
public class OAuth2InvalidGrantError(public val cause: OAuth2Exception.InvalidGrant) :
    AuthenticationFailedCause.Error("Failed to request OAuth2 access token due to invalid_grant: ${cause.message}")

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

    if (cause is OAuth2InvalidGrantError) {
        this@oauth2.fallback.invoke(call, cause)
        if (call.isHandled) {
            context.error(OAuthKey, cause)
            return
        }
    } else if (cause is AuthenticationFailedCause.Error) {
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
    Logger.trace("OAuth invalid grant reported", cause)
    OAuth2InvalidGrantError(cause)
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Logger.trace("OAuth2 request access token failed", cause)
    AuthenticationFailedCause.Error("Failed to request OAuth2 access token due to $cause")
}
