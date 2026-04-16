/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.application.*
import kotlinx.io.IOException

internal actual suspend fun OAuthAuthenticationProvider.oauth1a(
    authProviderName: String?,
    context: AuthenticationContext
) {
    val call = context.call
    val provider = providerLookup?.invoke(call) ?: settings
    if (provider !is OAuthServerSettings.OAuth1aServerSettings) return

    val token = call.oauth1aHandleCallback()
    val cause: AuthenticationFailedCause? = if (token == null) {
        AuthenticationFailedCause.NoCredentials
    } else {
        oauth1RequestToken(authProviderName, provider, token, context)
    }

    cause ?: return
    if (cause is AuthenticationFailedCause.Error) {
        runFallback(call, cause, context)
        return
    }

    @Suppress("NAME_SHADOWING")
    context.challenge(OAuthKey, cause) { challenge, call ->
        try {
            val t = simpleOAuth1aStep1(client, provider, call.urlProvider(provider))
            call.redirectAuthenticateOAuth1a(provider, t)
            challenge.complete()
        } catch (ioe: IOException) {
            runFallback(call, AuthenticationFailedCause.Error(ioe.message ?: "IOException"), context)
        }
    }
}

private suspend fun OAuthAuthenticationProvider.runFallback(
    call: ApplicationCall,
    cause: AuthenticationFailedCause.Error,
    context: AuthenticationContext
) {
    fallback.invoke(call, cause)
    context.error(OAuthKey, cause)
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
} catch (_: OAuth1aException.MissingTokenException) {
    AuthenticationFailedCause.InvalidCredentials
} catch (cause: Throwable) {
    AuthenticationFailedCause.Error("OAuth1a failed to get OAuth1 access token due to $cause")
}

internal fun String.appendUrlParameters(parameters: String) =
    when {
        parameters.isEmpty() -> ""
        this.endsWith("?") -> ""
        "?" in this -> "&"
        else -> "?"
    }.let { separator -> "$this$separator$parameters" }
