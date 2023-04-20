/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

private val Logger: Logger = KtorSimpleLogger("io.ktor.auth.oauth")

internal actual suspend fun OAuthAuthenticationProvider.oauth1a(
    authProviderName: String?,
    context: AuthenticationContext
) {
    throw NotImplementedError("OAuth1 is not supported on native targets")
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
): Unit = error("Not supported on Native")

/**
 * Responds with OAuth redirect.
 */
@Deprecated("Install and configure OAuth instead.", level = DeprecationLevel.ERROR)
public actual suspend fun PipelineContext<Unit, ApplicationCall>.oauthRespondRedirect(
    client: HttpClient,
    dispatcher: CoroutineDispatcher,
    provider: OAuthServerSettings,
    callbackUrl: String
): Unit = error("Not supported on Native")

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
): Unit = error("Not supported on Native")

/**
 * Handles an OAuth callback.
 */
@Deprecated(
    "Specifying an extra configuration function will be deprecated. Please provide it via OAuthServerSettings.",
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
): Unit = error("Not supported on Native")
