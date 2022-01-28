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
 * OAuth provider key
 */
public val OAuthKey: Any = "OAuth"

/**
 * Represents an OAuth provider for [Authentication] plugin
 */
public class OAuthAuthenticationProvider internal constructor(config: Config) : AuthenticationProvider(config) {

    internal val client: HttpClient = config.client
    internal val providerLookup: ApplicationCall.() -> OAuthServerSettings? = config.providerLookup
    internal val urlProvider: ApplicationCall.(OAuthServerSettings) -> String = config.urlProvider

    override suspend fun onAuthenticate(context: AuthenticationContext) {
        oauth1a(context)
        oauth2(context)
    }

    /**
     * OAuth provider configuration
     */
    public class Config internal constructor(name: String?) : AuthenticationProvider.Config(name) {
        /**
         * HTTP client instance used by this provider to make HTTP calls to OAuth server
         */
        public lateinit var client: HttpClient

        /**
         * Lookup function to find OAuth server settings for the particular call
         */
        public lateinit var providerLookup: ApplicationCall.() -> OAuthServerSettings?

        /**
         * URL provider that should produce login url for the particular call
         */
        public lateinit var urlProvider: ApplicationCall.(OAuthServerSettings) -> String

        internal fun build() = OAuthAuthenticationProvider(this)
    }
}

/**
 * Installs OAuth Authentication mechanism
 */
public fun AuthenticationConfig.oauth(
    name: String? = null,
    configure: OAuthAuthenticationProvider.Config.() -> Unit
) {
    val provider = OAuthAuthenticationProvider.Config(name).apply(configure).build()
    register(provider)
}

internal suspend fun OAuthAuthenticationProvider.oauth2(context: AuthenticationContext) {
    val call = context.call
    val provider = call.providerLookup()
    if (provider !is OAuthServerSettings.OAuth2ServerSettings) return

    val token = call.oauth2HandleCallback()
    val callbackRedirectUrl = call.urlProvider(provider)
    val cause: AuthenticationFailedCause? = if (token == null) {
        AuthenticationFailedCause.NoCredentials
    } else {
        oauth2RequestToken(provider, callbackRedirectUrl, token, context)
    }

    cause ?: return
    @Suppress("NAME_SHADOWING")
    context.challenge(OAuthKey, cause) { challenge, call ->
        call.redirectAuthenticateOAuth2(
            provider,
            callbackRedirectUrl,
            state = provider.nonceManager.newNonce(),
            scopes = provider.defaultScopes,
            interceptor = provider.authorizeUrlInterceptor
        )
        challenge.complete()
    }
}

internal suspend fun OAuthAuthenticationProvider.oauth1a(context: AuthenticationContext) {
    val call = context.call
    val provider = call.providerLookup()
    if (provider !is OAuthServerSettings.OAuth1aServerSettings) return

    val token = call.oauth1aHandleCallback()
    val cause: AuthenticationFailedCause? = if (token == null) {
        AuthenticationFailedCause.NoCredentials
    } else {
        oauth1RequestToken(provider, token, context)
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
    provider: OAuthServerSettings.OAuth1aServerSettings,
    token: OAuthCallback.TokenPair,
    context: AuthenticationContext
) = try {
    val accessToken = requestOAuth1aAccessToken(client, provider, token)
    context.principal(accessToken)
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
    provider: OAuthServerSettings.OAuth2ServerSettings,
    callbackRedirectUrl: String,
    token: OAuthCallback.TokenSingle,
    context: AuthenticationContext
) = try {
    val accessToken = oauth2RequestAccessToken(client, provider, callbackRedirectUrl, token)
    context.principal(accessToken)
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
