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

private const val HEADER_LOG_LIMIT: Int = 96

@OptIn(InternalAPI::class)
internal fun <P : Any> OidcProvider<P>.createBearerScheme(): DefaultAuthScheme<P, AuthenticatedContext<P>> {
    val extractor = bearerConfig.tokenExtractor
    return bearer(
        name = "$name-bearer",
        principalType = principalType,
        contextFactory = { it },
    ) {
        description = "OpenID Connect Bearer"

        authHeader { call -> call.extractBearerHeader(extractor, logger.takeIf { developmentMode }) }

        authenticate { credential ->
            runCatching {
                val token = verifyAccessToken(credential.token)
                transformPrincipal(token)
            }.onFailure { cause ->
                if (cause is CancellationException) throw cause
                logger.trace("OpenID access token authentication failed $cause")
            }.getOrNull()
        }

        onUnauthorized = {
            val challenge = HttpAuthHeader.Parameterized(AuthScheme.Bearer, parameters = emptyMap())
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
            config.codeChallengeMethod?.let { method ->
                parameters.append("code_challenge", transaction.codeChallenge())
                parameters.append("code_challenge_method", method.name)
            }
        },
        verifyState = { call, state ->
            call.validateAuthorizationResponseIssuer(currentMetadata())
            state != null && call.readAuthorizationTransaction(stateCodec, state) != null
        },
        extraTokenParametersProvider = provider@{ call, callback ->
            if (config.codeChallengeMethod == null) {
                return@provider emptyList()
            }
            val transaction = call.readAuthorizationTransaction(stateCodec, callback.state)
            transaction?.let { listOf("code_verifier" to it.codeVerifier) }.orEmpty()
        },
        onStateCreated = { call, state ->
            val method = checkNotNull(config.codeChallengeMethod) { "PKCE is disabled" }
            call.createAuthorizationTransaction(stateCodec, method, state)
        },
    )
}

private fun ApplicationCall.extractBearerHeader(extractor: TokenExtractor?, logger: Logger?): HttpAuthHeader? {
    if (extractor != null) {
        val blob = extractor(this) ?: return null
        return HttpAuthHeader.Single(AuthScheme.Bearer, blob)
    }
    val header = request.headers[HttpHeaders.Authorization] ?: return null
    val bearer = runCatching { parseAuthorizationHeader(header) }
        .onFailure { cause ->
            logger?.trace(
                "Malformed OpenID Connect Authorization header ignored: '{}': {}",
                header.truncateForLog(),
                cause.message,
            )
        }.getOrNull()
    if (bearer !is HttpAuthHeader.Single || bearer.authScheme != AuthScheme.Bearer) {
        return null
    }
    return HttpAuthHeader.Single(AuthScheme.Bearer, bearer.blob)
}

private fun String.truncateForLog(): String {
    val sanitized = replace('\r', ' ').replace('\n', ' ')
    return if (sanitized.length <= HEADER_LOG_LIMIT) {
        sanitized
    } else {
        sanitized.take(HEADER_LOG_LIMIT) + "..."
    }
}
