/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth")

internal actual suspend fun OAuthAuthenticationProvider.oauth1a(
    authProviderName: String?,
    context: AuthenticationContext
) {
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

/**
 * Installs both OAuth1a and OAuth2 authentication helpers that redirects to an OAuth server authorization page
 * and handles corresponding callbacks.
 */
@Suppress("unused")
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public actual suspend fun PipelineContext<Unit, ApplicationCall>.oauth(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    providerLookup: ApplicationCall.() -> OAuthServerSettings?,
    urlProvider: ApplicationCall.(OAuthServerSettings) -> String
) {
    oauth1a(client, dispatcher, providerLookup, urlProvider)
    oauth2(client, dispatcher, providerLookup, urlProvider)
}

/**
 * Responds with OAuth redirect.
 */
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public actual suspend fun PipelineContext<Unit, ApplicationCall>.oauthRespondRedirect(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String
) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            withContext(dispatcher) {
                val requestToken = simpleOAuth1aStep1(client, provider, callbackUrl)
                call.redirectAuthenticateOAuth1a(provider, requestToken)
            }
        }

        is OAuthServerSettings.OAuth2ServerSettings -> {
            call.redirectAuthenticateOAuth2(
                provider,
                callbackUrl,
                provider.nonceManager.newNonce(),
                scopes = provider.defaultScopes,
                interceptor = provider.authorizeUrlInterceptor
            )
        }
    }
}

/**
 * Handles an OAuth callback. Usually it leads to requesting an access token.
 */
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public actual suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String,
    loginPageUrl: String,
    block: suspend (OAuthAccessTokenResponse) -> Unit
) {
    @Suppress("DEPRECATION_ERROR")
    oauthHandleCallback(client, dispatcher, provider, callbackUrl, loginPageUrl, {}, block)
}

/**
 * Handles an OAuth callback.
 */
@Deprecated(
    "Specifying an extra configuration function will be deprecated. " +
        "Please provide it via OAuthServerSettings.",
    level = DeprecationLevel.ERROR
)
public actual suspend fun PipelineContext<Unit, ApplicationCall>.oauthHandleCallback(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String,
    loginPageUrl: String,
    configure: HttpRequestBuilder.() -> Unit,
    block: suspend (OAuthAccessTokenResponse) -> Unit
) {
    when (provider) {
        is OAuthServerSettings.OAuth1aServerSettings -> {
            val tokens = call.oauth1aHandleCallback()
            if (tokens == null) {
                call.respondRedirect(loginPageUrl)
            } else {
                withContext(dispatcher) {
                    try {
                        val accessToken = requestOAuth1aAccessToken(client, provider, tokens)
                        block(accessToken)
                    } catch (ioe: IOException) {
                        call.oauthHandleFail(loginPageUrl)
                    }
                }
            }
        }

        is OAuthServerSettings.OAuth2ServerSettings -> {
            val code = call.oauth2HandleCallback()
            if (code == null) {
                call.respondRedirect(loginPageUrl)
            } else {
                withContext(dispatcher) {
                    try {
                        val accessToken = oauth2RequestAccessToken(
                            client,
                            provider,
                            callbackUrl,
                            code,
                            configure
                        )

                        block(accessToken)
                    } catch (cause: OAuth2Exception.InvalidGrant) {
                        Logger.trace("Redirected to the login page due to invalid_grant error: ${cause.message}")
                        call.oauthHandleFail(loginPageUrl)
                    } catch (ioe: IOException) {
                        Logger.trace("Redirected to the login page due to IO error", ioe)
                        call.oauthHandleFail(loginPageUrl)
                    }
                }
            }
        }
    }
}

internal suspend fun ApplicationCall.oauthHandleFail(redirectUrl: String) = respondRedirect(redirectUrl)

internal fun String.appendUrlParameters(parameters: String) =
    when {
        parameters.isEmpty() -> ""
        this.endsWith("?") -> ""
        "?" in this -> "&"
        else -> "?"
    }.let { separator -> "$this$separator$parameters" }
