/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException

internal fun Application.configureOAuthRoute(provider: OidcProvider) {
    val config = provider.oauthConfig

    routing {
        val sessionsDisabled = config.sessionsDisabled || config.sessionConfig == null
        if (sessionsDisabled) {
            install(provider.oauthFlow)
            return@routing
        }

        install(provider.oauthSessionFlow)

        authenticateWith(provider.session) {
            config.refreshPath?.let { path ->
                post(path) {
                    val refreshToken = call.session.refreshToken ?: run {
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

                    call.session = refreshedPrincipal
                    config.onRefresh(this)
                    if (!call.isHandled) {
                        call.respond(HttpStatusCode.OK)
                    }
                }
            }

            config.logoutPath?.let { path ->
                requireNotNull(provider.currentMetadata().endSessionEndpoint) {
                    "Identity provider ${provider.name} doesn't support logout"
                }

                post(path) {
                    val postLogoutRedirectUri = config.postLogoutRedirectUri?.let { builder ->
                        call.request.oidcRedirectUri(builder)
                    }
                    val idTokenHint = call.session.value
                    call.clearSession()

                    config.onLogout(this)
                    if (call.isHandled) {
                        return@post
                    }

                    val logoutUrl = provider.buildLogoutUrl(idTokenHint, postLogoutRedirectUri)
                    call.response.headers.append(HttpHeaders.Location, logoutUrl)
                    call.respond(HttpStatusCode.SeeOther)
                }
            }
        }
    }
}

context(context: RoutingContext)
internal suspend fun OidcProvider.handleOAuthCallbackSuccess(
    response: OAuthAccessTokenResponse.OAuth2,
): OidcToken.Id {
    val call = context.call
    call.validateAuthorizationResponseIssuer(currentMetadata())
    val oauthState = response.state ?: call.request.queryParameters["state"]
    val authorizationTransaction = oauthState?.let {
        call.consumeAuthorizationTransaction(stateCodec, it)
    }
    return buildOAuthToken(response, expectedNonce = authorizationTransaction?.nonce)
}

internal fun ApplicationCall.validateAuthorizationResponseIssuer(metadata: OpenIdProviderMetadata) {
    val responseIssuer = request.queryParameters["iss"]
    if (responseIssuer == null) {
        require(metadata.authorizationResponseIssParameterSupported != true) {
            "OpenID Connect authorization response is missing 'iss' parameter"
        }
        return
    }
    require(responseIssuer == metadata.issuer) {
        "OpenID Connect authorization response issuer mismatch: expected ${metadata.issuer}, got $responseIssuer"
    }
}
