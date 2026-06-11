/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

@OptIn(ExperimentalKtorApi::class, InternalAPI::class)
internal fun <P : Any> Application.configureOAuthRoute(provider: OidcProvider<P>) {
    val config = provider.oauthConfig
    val loginPath = oidcRoutePath(config.loginUri)
    val redirectPath = oidcRoutePath(config.redirectUri)

    routing {
        // Manual login redirect: generates per-request state + OIDC nonce and redirects to IDP.
        get(loginPath) {
            val oauthState = generateNonceSuspend()
            val authorizationTransaction = call.createAuthorizationTransaction(provider.stateCodec, oauthState)

            val redirectUriStr = call.request.oidcRedirectUri(config.redirectUri)

            val metadata = provider.currentMetadata()
            val authorizeUrl = URLBuilder(metadata.authorizationEndpoint).apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", config.clientId)
                parameters.append("redirect_uri", redirectUriStr)
                parameters.append("scope", config.scopes.joinToString(" "))
                parameters.append("state", oauthState)
                parameters.append("nonce", authorizationTransaction.nonce)
                config.resourceIndicators.forEach { parameters.append("resource", it) }
            }.buildString()

            call.respondRedirect(authorizeUrl)
        }

        oauthCallback(
            flow = provider.oauthFlow,
            path = redirectPath,
            onSuccess = { provider.handleOAuthCallbackSuccess(response = principal) }
        )
    }
}

context(context: RoutingContext)
private suspend fun <P : Any> OidcProvider<P>.handleOAuthCallbackSuccess(
    response: OAuthAccessTokenResponse.OAuth2,
) {
    val config = oauthConfig
    val call = context.call
    try {
        call.validateAuthorizationResponseIssuer(currentMetadata())
        val oauthState = response.state ?: call.request.queryParameters["state"]
        val authorizationTransaction = oauthState?.let {
            call.consumeAuthorizationTransaction(stateCodec, it)
        }
        val token = buildOAuthToken(response, expectedNonce = authorizationTransaction?.nonce)
        val typedPrincipal = transformPrincipal(token) ?: run {
            val error = AuthenticationFailedCause.Error("OpenID Connect principal was not accepted")
            return config.onFailure(context, error)
        }
        config.onSuccess(context, typedPrincipal)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        logger.debug("OpenID Connect OAuth callback failed: {}", cause.message)
        val error = AuthenticationFailedCause.Error("OpenID Connect OAuth callback failed")
        config.onFailure(context, error)
    }
}

internal fun ApplicationCall.validateAuthorizationResponseIssuer(metadata: OpenIdProviderMetadata) {
    val responseIssuer = request.queryParameters["iss"]
    if (responseIssuer == null) {
        require(metadata.authorizationResponseIssParameterSupported != true) {
            "OpenID Connect authorization response is missing iss parameter"
        }
        return
    }
    require(responseIssuer == metadata.issuer) {
        "OpenID Connect authorization response issuer mismatch: expected ${metadata.issuer}, got $responseIssuer"
    }
}
