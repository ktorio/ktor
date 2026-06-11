/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.http.auth.AuthScheme
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.typesafe.*
import io.ktor.server.response.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import org.slf4j.Logger

private const val AuthorizationHeaderLogLimit: Int = 96

@OptIn(InternalAPI::class)
internal fun <P : Any> OidcProvider<P>.createBearerScheme(): OidcBearerScheme<P> {
    val extractor = bearerConfig.tokenExtractor
    return bearer(
        name = "$name-bearer",
        principalType = principalType,
        contextFactory = { default -> OidcBearerContext(default, provider = this) },
    ) {
        description = "OpenID Connect Bearer"

        authHeader { call -> call.extractBearerHeader(extractor, logger) }

        authenticate { credential ->
            runCatching {
                val token = verifyAccessToken(credential.token)
                transformPrincipal(token)
            }.onFailure {
                if (it is CancellationException) throw it
                logger.trace("OpenID access token authentication failed. {}", it.message)
            }.getOrNull()
        }

        onUnauthorized = { _ ->
            val challenge = HttpAuthHeader.Parameterized(
                authScheme = AuthScheme.Bearer,
                parameters = emptyMap(),
            )
            call.respond(UnauthorizedResponse(challenge))
        }
    }
}

internal fun <P : Any> OidcProvider<P>.createOauthFlow(): OAuth2Flow =
    oauth2Flow(name) {
        client = this@createOauthFlow.client
        settings = oauthServerSettings()
        urlProvider = { call.request.oidcRedirectUri(oauthConfig.redirectUri) }
        onForbidden = onForbidden@{ cause ->
            val message = (cause as? AuthenticationFailedCause.Error)?.message ?: cause.toString()
            logger.debug("OAuth authentication failed for: {}", message)
            oauthConfig.onFailure.invoke(this@onForbidden, cause)
        }
    }

private fun OidcProvider<*>.oauthServerSettings(): OAuthServerSettings.OAuth2ServerSettings {
    val config = oauthConfig
    val metadata = currentMetadata()
    return OAuthServerSettings.OAuth2ServerSettings(
        name = name,
        authorizeUrl = metadata.authorizationEndpoint,
        accessTokenUrl = metadata.tokenEndpoint,
        requestMethod = HttpMethod.Post,
        clientId = config.clientId,
        clientSecret = config.clientSecret,
        defaultScopes = config.scopes,
        extraAuthParameters = config.resourceIndicators.map { "resource" to it },
        extraTokenParameters = config.resourceIndicators.map { "resource" to it },
        authorizeUrlInterceptor = authorize@{ request ->
            val state = parameters[OAuth2RequestParameters.State]
            val transaction = state?.let {
                request.call.readAuthorizationTransaction(stateCodec, it)
            } ?: return@authorize
            parameters.append("nonce", transaction.nonce)
            if (config.pkceEnabled) {
                parameters.append("code_challenge", transaction.codeChallenge())
                parameters.append("code_challenge_method", PkceCodeChallengeMethod)
            }
        },
        verifyState = { call, state ->
            call.validateAuthorizationResponseIssuer(currentMetadata())
            state != null && call.readAuthorizationTransaction(stateCodec, state) != null
        },
        extraTokenParametersProvider = provider@{ call, callback ->
            if (!config.pkceEnabled) {
                return@provider emptyList()
            }
            val transaction = call.readAuthorizationTransaction(stateCodec, callback.state)
            transaction?.let { listOf("code_verifier" to it.codeVerifier) }.orEmpty()
        },
        onStateCreated = { call, state -> call.createAuthorizationTransaction(stateCodec, state) },
    )
}

private fun ApplicationCall.extractBearerHeader(extractor: TokenExtractor?, logger: Logger): HttpAuthHeader? {
    val token = if (extractor == null) {
        val header = request.headers[HttpHeaders.Authorization] ?: return null
        val parsed = runCatching {
            parseAuthorizationHeader(headerValue = header)
        }.onFailure { cause ->
            logger.trace(
                "Malformed OpenID Connect Authorization header ignored: '{}': {}",
                header.truncateForLog(),
                cause.message,
            )
        }.getOrNull()
        val bearer = parsed as? HttpAuthHeader.Single
        bearer?.takeIf { it.authScheme == AuthScheme.Bearer }?.blob
    } else {
        extractor(this)
    }
    val blob = token?.takeIf { it.isNotBlank() } ?: return null
    return HttpAuthHeader.Single(authScheme = AuthScheme.Bearer, blob = blob)
}

private fun String.truncateForLog(): String {
    val sanitized = replace('\r', ' ').replace('\n', ' ')
    return if (sanitized.length <= AuthorizationHeaderLogLimit) {
        sanitized
    } else {
        sanitized.take(AuthorizationHeaderLogLimit) + "..."
    }
}
