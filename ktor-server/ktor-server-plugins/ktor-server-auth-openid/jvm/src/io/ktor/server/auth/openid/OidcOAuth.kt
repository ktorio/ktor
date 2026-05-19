/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.openid

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
    val oauthConfig = provider.config.oauthConfig ?: return
    val oauth = checkNotNull(provider.oauthFlow)

    val loginPath = oidcRoutePath(oauthConfig.loginUri)
    val redirectPath = oidcRoutePath(oauthConfig.redirectUri)

    routing {
        // Manual login redirect: generates per-request state + OIDC nonce and redirects to IDP.
        get(loginPath) {
            val oauthState = generateNonceSuspend()
            val authorizationTransaction = provider.createAuthorizationTransaction(call, oauthState)

            val redirectUriStr = call.request.oidcRedirectUri(oauthConfig.redirectUri)

            val metadata = provider.currentMetadata()
            val authorizeUrl = URLBuilder(metadata.authorizationEndpoint).apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", oauthConfig.clientId)
                parameters.append("redirect_uri", redirectUriStr)
                parameters.append("scope", oauthConfig.scopes.joinToString(" "))
                parameters.append("state", oauthState)
                parameters.append("nonce", authorizationTransaction.nonce)
            }.buildString()

            call.respondRedirect(authorizeUrl)
        }

        oauthCallback(
            flow = oauth,
            path = redirectPath,
            onSuccess = { handleOAuthCallbackSuccess(provider, oauthConfig, oauthResponse = principal) }
        )
    }
}

private suspend fun <P : Any> RoutingContext.handleOAuthCallbackSuccess(
    provider: OidcProvider<P>,
    oauthConfig: OidcOAuthConfig<P>,
    oauthResponse: OAuthAccessTokenResponse.OAuth2,
) {
    try {
        provider.validateAuthorizationResponseIssuer(call)
        val oauthState = oauthResponse.state ?: call.request.queryParameters["state"]
        val authorizationTransaction = oauthState?.let { provider.consumeAuthorizationTransaction(call, state = it) }
        val rawPrincipal = provider.buildOAuthPrincipal(oauthResponse, expectedNonce = authorizationTransaction?.nonce)
        val typedPrincipal = provider.transformPrincipal(this, rawPrincipal) ?: run {
            val error = AuthenticationFailedCause.Error("OpenID Connect principal was not accepted")
            return oauthConfig.onFailure.invoke(this, error)
        }
        oauthConfig.onSuccess(this, typedPrincipal)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        provider.logger.debug("OpenID Connect OAuth callback failed: {}", cause::class.simpleName)
        val error = AuthenticationFailedCause.Error("OpenID Connect OAuth callback failed")
        oauthConfig.onFailure.invoke(this, error)
    }
}

internal fun OidcProvider<*>.validateAuthorizationResponseIssuer(call: ApplicationCall) {
    val metadata = currentMetadata()
    val responseIssuer = call.request.queryParameters["iss"]
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
