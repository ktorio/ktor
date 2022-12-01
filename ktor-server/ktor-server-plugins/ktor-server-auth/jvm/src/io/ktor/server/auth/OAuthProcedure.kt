/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.server.application.*
import org.slf4j.*
import java.io.*

private val Logger: Logger = LoggerFactory.getLogger("io.ktor.auth.oauth")

/**
 * An OAuth provider key.
 */
public val OAuthKey: Any = "OAuth"

/**
 * An `OAuth` [Authentication] provider.
 *
 * @see [oauth]
 */
public class OAuthAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    internal val client: HttpClient = config.client
    internal val providerLookup: ApplicationCall.() -> OAuthServerSettings? = config.providerLookup
    internal val urlProvider: ApplicationCall.(OAuthServerSettings) -> String = config.urlProvider

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        oauth1a(name, context)
        oauth2(name, context)
    }

    /**
     * A configuration for the [oauth] authentication provider.
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /**
         * An HTTP client instance used to make requests to the OAuth server.
         */
        public lateinit var client: HttpClient

        /**
         * A lookup function to find OAuth server settings for the particular call.
         */
        public lateinit var providerLookup: ApplicationCall.() -> OAuthServerSettings?

        /**
         * Specifies a redirect route that is opened when authorization is completed.
         */
        public lateinit var urlProvider: ApplicationCall.(OAuthServerSettings) -> String

        internal fun build() = OAuthAuthenticationProvider(this)
    }
}

/**
 * Installs the OAuth [Authentication] provider.
 * OAuth can be used to authorize users of your application by using external providers,
 * such as Google, Facebook, Twitter, and so on.
 * To learn how to configure it, see [OAuth](https://ktor.io/docs/oauth.html).
 */
public fun AuthenticationConfig.oauth(
    name: String? = null,
    configure: OAuthAuthenticationProvider.Config.() -> Unit
) {
    val provider = OAuthAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

internal suspend fun OAuthAuthenticationProvider.oauth2(authProviderName: String?, context: AuthenticationContext) {
    val call = context.call
    val provider = call.providerLookup()
    if (provider !is OAuthServerSettings.OAuth2ServerSettings) return

    val token = call.oauth2HandleCallback()
    val callbackRedirectUrl = call.urlProvider(provider)
    val cause: AuthenticationFailedCause? = if (token == null) {
        AuthenticationFailedCause.NoCredentials
    } else {
        oauth2RequestToken(authProviderName, provider, callbackRedirectUrl, token, context)
    }

    cause ?: return
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

internal suspend fun OAuthAuthenticationProvider.oauth1a(authProviderName: String?, context: AuthenticationContext) {
    val call = context.call
    val provider = call.providerLookup()
    if (provider !is OAuthServerSettings.OAuth1aServerSettings) return

    val token = call.oauth1aHandleCallback()
    val cause: AuthenticationFailedCause? = if (token == null) {
        AuthenticationFailedCause.NoCredentials
    } else {
        oauth1RequestToken(authProviderName, provider, token, context)
    }

    if (cause != null) {
        @Suppress("NAME_SHADOWING")
        context.challenge(OAuthKey, cause) { challenge, call ->
            try {
                val t = simpleOAuth1aStep1(client, provider, call.urlProvider(provider))
                call.redirectAuthenticateOAuth1a(provider, t)
                challenge.complete()
            } catch (ioe: IOException) {
                context.error(OAuthKey, AuthenticationFailedCause.Error(ioe.message ?: "IOException"))
            }
        }
    }
}

private suspend fun OAuthAuthenticationProvider.oauth1RequestToken(
    authProviderName: String?,
    provider: OAuthServerSettings.OAuth1aServerSettings,
    token: OAuthCallback.TokenPair,
    context: AuthenticationContext
) = try {
    val accessToken = requestOAuth1aAccessToken(client, provider, token)
    context.principal(authProviderName, accessToken)
    null
} catch (cause: OAuth1aException.MissingTokenException) {
    AuthenticationFailedCause.InvalidCredentials
} catch (cause: Throwable) {
    context.error(
        OAuthKey,
        AuthenticationFailedCause.Error("OAuth1a failed to get OAuth1 access token")
    )
    null
}

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
    Logger.trace("OAuth invalid grant reported: {}", cause.message)
    AuthenticationFailedCause.InvalidCredentials
} catch (cause: Throwable) {
    Logger.trace("OAuth2 request access token failed", cause)
    context.error(
        OAuthKey,
        AuthenticationFailedCause.Error("Failed to request OAuth2 access token due to $cause")
    )
    null
}
