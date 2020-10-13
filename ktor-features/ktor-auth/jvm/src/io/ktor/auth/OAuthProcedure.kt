/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.auth

import io.ktor.application.*
import io.ktor.client.*
import org.slf4j.*
import java.io.*

private val Logger: Logger = LoggerFactory.getLogger("io.ktor.auth.oauth")

/**
 * OAuth provider key
 */
public val OAuthKey: Any = "OAuth"

/**
 * Represents an OAuth provider for [Authentication] feature
 */
public class OAuthAuthenticationProvider internal constructor(config : Configuration) : AuthenticationProvider(config) {

    internal val client: HttpClient = config.client
    internal val providerLookup: ApplicationCall.() -> OAuthServerSettings? = config.providerLookup
    internal val urlProvider: ApplicationCall.(OAuthServerSettings) -> String = config.urlProvider

    /**
     * OAuth provider configuration
     */
    public class Configuration internal constructor(name: String?) : AuthenticationProvider.Configuration(name) {
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
public fun Authentication.Configuration.oauth(
    name: String? = null,
    configure: OAuthAuthenticationProvider.Configuration.() -> Unit
) {
    val provider = OAuthAuthenticationProvider.Configuration(name).apply(configure).build()
    provider.oauth1a()
    provider.oauth2()
    register(provider)
}

internal fun OAuthAuthenticationProvider.oauth2() {
    pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth2ServerSettings) {
            val token = call.oauth2HandleCallback()
            val callbackRedirectUrl = call.urlProvider(provider)
            val cause: AuthenticationFailedCause? = if (token == null) {
                AuthenticationFailedCause.NoCredentials
            } else {
                try {
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
            }

            if (cause != null) {
                context.challenge(OAuthKey, cause) {
                    call.redirectAuthenticateOAuth2(
                        provider, callbackRedirectUrl,
                        state = provider.nonceManager.newNonce(),
                        scopes = provider.defaultScopes,
                        interceptor = provider.authorizeUrlInterceptor
                    )
                    it.complete()
                }
            }
        }
    }
}

internal fun OAuthAuthenticationProvider.oauth1a() {
    pipeline.intercept(AuthenticationPipeline.RequestAuthentication) { context ->
        val provider = call.providerLookup()
        if (provider is OAuthServerSettings.OAuth1aServerSettings) {
            val token = call.oauth1aHandleCallback()
            val cause: AuthenticationFailedCause? = if (token == null) {
                AuthenticationFailedCause.NoCredentials
            } else {
                try {
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
            }

            if (cause != null) {
                context.challenge(OAuthKey, cause) { ch ->
                    try {
                        val t = simpleOAuth1aStep1(client, provider, call.urlProvider(provider))
                        call.redirectAuthenticateOAuth1a(provider, t)
                        ch.complete()
                    } catch (ioe: IOException) {
                        context.error(OAuthKey, AuthenticationFailedCause.Error(ioe.message ?: "IOException"))
                    }
                }
            }
        }
    }
}
