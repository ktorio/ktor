/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.serialization.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.Logger

private const val HEADER_LOG_LIMIT: Int = 96

internal fun OidcProvider.createJwtBearerScheme(
    resourceMetadataUrl: String?,
): SimpleAuthenticationScheme<OidcToken.Access> {
    val extractor = bearerConfig.tokenExtractor
    val typedConfig = TypedBearerAuthConfig<OidcToken.Access>().apply {
        description = "OpenID Connect JWT Bearer"

        authHeader { extractBearerHeader(extractor, logger.takeIf { developmentMode }) }

        validate { credential ->
            runCatching {
                verifyJwtAccessToken(credential.token)
            }.onFailure { cause ->
                if (cause is CancellationException) throw cause
                logger.trace("OpenID JWT access token authentication failed $cause")
            }.getOrNull()
        }

        onUnauthorized = {
            val parameters = resourceMetadataUrl?.let { mapOf("resource_metadata" to it) }.orEmpty()
            val challenge = HttpAuthHeader.Parameterized(AuthScheme.Bearer, parameters = parameters)
            call.respond(UnauthorizedResponse(challenge))
        }
    }

    return AuthenticationScheme.from(
        provider = typedConfig.buildProvider(name = "$name-jwt-bearer"),
        onUnauthorized = typedConfig.onUnauthorized,
    )
}

internal fun OidcProvider.createIntrospectionBearerScheme(
    resourceMetadataUrl: String?,
): SimpleAuthenticationScheme<OidcToken.Introspected> {
    val extractor = bearerConfig.tokenExtractor
    val typedConfig = TypedBearerAuthConfig<OidcToken.Introspected>().apply {
        description = "OpenID Connect Introspection Bearer"

        authHeader { extractBearerHeader(extractor, logger.takeIf { developmentMode }) }

        validate { credential ->
            runCatching {
                verifyIntrospectedToken(credential.token)
            }.onFailure { cause ->
                if (cause is CancellationException) throw cause
                logger.trace("OpenID introspection access token authentication failed $cause")
            }.getOrNull()
        }

        onUnauthorized = {
            val parameters = resourceMetadataUrl?.let { mapOf("resource_metadata" to it) }.orEmpty()
            val challenge = HttpAuthHeader.Parameterized(AuthScheme.Bearer, parameters = parameters)
            call.respond(UnauthorizedResponse(challenge))
        }
    }

    return AuthenticationScheme.from(
        provider = typedConfig.buildProvider(name = "$name-introspection-bearer"),
        onUnauthorized = typedConfig.onUnauthorized,
    )
}

internal val OidcProvider.oauthFailureHandler: UnauthorizedHandler
    get() = UnauthorizedHandler { cause ->
        val message = (cause as? AuthenticationFailedCause.Error)?.message ?: cause.toString()
        logger.debug("OAuth authentication failed for: {}", message)
        with(oauthConfig.onAuthenticationFailed) { onUnauthorized(cause) }
    }

internal fun OidcProvider.createOauthFlow(): OAuth2Flow {
    val config = oauthConfig
    val loginPath = oidcRoutePath(config.loginUri)
    val redirectPath = oidcRoutePath(config.redirectUri)

    return oauth2(name) {
        client = this@createOauthFlow.client
        settings = oauthServerSettings()
        onUnauthorized = oauthFailureHandler
        this.loginPath = loginPath

        callback(redirectPath) callback@{ response ->
            try {
                val token = handleOAuthCallbackSuccess(response)
                config.onAuthenticated(this, token)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                val failure = AuthenticationFailedCause.Error("Failed to complete OpenID Connect callback $cause")
                return@callback with(oauthFailureHandler) { onUnauthorized(failure) }
            }
        }
    }
}

internal fun OidcProvider.createOAuthSession(
    secure: Boolean
): OAuth2SessionFlow<OidcToken.Id, OidcToken.Id> {
    val config = oauthConfig
    val sessionConfig = sessionConfig
    val loginPath = oidcRoutePath(config.loginUri)
    val redirectPath = oidcRoutePath(config.redirectUri)

    val sessionFormat = Json { ignoreUnknownKeys = true }
    val sessionSerializer = KotlinxSessionSerializer(OidcToken.Id.serializer(), sessionFormat)

    val sessionFlowConfig = OAuthSessionFlowConfig<OidcToken.Id, OidcToken.Id>().apply {
        client = this@createOAuthSession.client
        settings = oauthServerSettings()
        onUnauthorized = oauthFailureHandler
        this.loginPath = loginPath

        callback(
            path = redirectPath,
            onFailure = config.onAuthenticationFailed,
            onSuccess = { config.onAuthenticated(this, call.session) },
        )

        sessions {
            sessionConfig.name?.let { name = it }

            transport = SessionTransportType.CookieId(sessionConfig.storage) {
                serializer = sessionSerializer
                cookie.httpOnly = true
                cookie.secure = secure
                cookie.extensions["SameSite"] = "lax"
                sessionConfig.cookieConfigure?.invoke(this)
            }

            sessionCreator = { response ->
                handleOAuthCallbackSuccess(response)
            }

            transformSession { refreshSessionIfNeeded(token = it) }

            validate { it }

            sessionConfig.csrfConfigurer?.let { configure ->
                csrfProtection(configure)
            }
        }
    }
    return OAuth2SessionFlow.from(
        name = name,
        config = sessionFlowConfig,
        principalType = OidcToken.Id::class,
        sessionTypeInfo = typeInfo<OidcToken.Id>(),
    )
}

internal fun OidcProvider.oauthServerSettings(): OAuthServerSettings.OAuth2ServerSettings {
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
            val method = config.codeChallengeMethod ?: CodeChallengeMethod.S256
            call.createAuthorizationTransaction(stateCodec, method, state)
        },
    )
}

private fun RoutingContext.extractBearerHeader(extractor: OidcTokenExtractor?, logger: Logger?): HttpAuthHeader? {
    if (extractor != null) {
        val blob = extractor() ?: return null
        return HttpAuthHeader.Single(AuthScheme.Bearer, blob)
    }
    val header = call.request.headers[HttpHeaders.Authorization] ?: return null
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
