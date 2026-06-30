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
            val configuredCodeChallengeMethod = config.codeChallengeMethod
            val transactionCodeChallengeMethod = configuredCodeChallengeMethod ?: CodeChallengeMethod.S256
            val authorizationTransaction =
                call.createAuthorizationTransaction(provider.stateCodec, transactionCodeChallengeMethod, oauthState)

            val redirectUriStr = call.request.oidcRedirectUri(config.redirectUri)

            val metadata = provider.currentMetadata()
            val authorizeUrl = URLBuilder(metadata.authorizationEndpoint).apply {
                parameters.append("response_type", "code")
                parameters.append("client_id", config.clientId)
                parameters.append("redirect_uri", redirectUriStr)
                parameters.append("scope", config.scopes.joinToString(" "))
                parameters.append("state", oauthState)
                parameters.append("nonce", authorizationTransaction.nonce)
                configuredCodeChallengeMethod?.let { method ->
                    parameters.append("code_challenge", authorizationTransaction.codeChallenge())
                    parameters.append("code_challenge_method", method.name)
                }
                config.resourceIndicators.forEach { parameters.append("resource", it) }
            }.buildString()

            call.respondRedirect(authorizeUrl)
        }

        val sessionsDisabled = provider.config.sessionConfig == null
        if (sessionsDisabled) {
            oauthCallback(
                flow = provider.oauthFlow,
                path = redirectPath,
                onSuccess = { provider.handleOAuthCallbackSuccess(response = principal) }
            )
            return@routing
        }

        oauthCallback(
            flow = provider.oauthSessionFlow,
            path = redirectPath,
            onFailure = config.onFailure,
            onSuccess = { config.onSuccess(this, principal) }
        )

        authenticateWith(provider.sessions) {
            post(provider.sessionRefreshPath) {
                val refreshToken = session.refreshToken ?: run {
                    provider.logger.debug("Session has no refresh token, cannot refresh")
                    return@post call.respond(HttpStatusCode.Unauthorized)
                }

                val refreshResult = try {
                    provider.refreshToken(refreshToken)
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: Exception) {
                    provider.logger.debug("Failed to refresh token", cause)
                    null
                }

                if (refreshResult == null) {
                    return@post call.respond(HttpStatusCode.Unauthorized)
                }

                val refreshedPrincipal = refreshResult.idToken
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                session = refreshedPrincipal
                call.respond(HttpStatusCode.OK)
            }

            post(provider.sessionLogoutPath) {
                val postLogoutRedirectUri = call.request.oidcRedirectUri(config.postLogoutRedirectUri)
                val idTokenHint = session.value
                clearSession()

                val logoutUrl = runCatching {
                    provider.buildLogoutUrl(idTokenHint, postLogoutRedirectUri)
                }.onFailure {
                    provider.logger.debug(
                        "Failed to build provider logout URL, redirecting to postLogoutRedirectUri instead",
                        it
                    )
                }.getOrDefault(postLogoutRedirectUri)

                call.response.headers.append(HttpHeaders.Location, logoutUrl)
                call.respond(HttpStatusCode.SeeOther)
            }
        }
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
