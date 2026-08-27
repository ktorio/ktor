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
import kotlinx.serialization.json.Json
import org.slf4j.Logger

private const val HEADER_LOG_LIMIT: Int = 96

internal fun OidcProvider.createJwtBearerScheme(): SimpleAuthenticationScheme<OidcToken.Access> {
    val typedConfig = TypedBearerAuthConfig<OidcToken.Access>().apply {
        val extractor = bearerConfig.tokenExtractor

        description = "OpenID Connect JWT Bearer"

        authHeader { extractBearerHeader(extractor, logger.takeIf { developmentMode }) }

        validate { credential ->
            try {
                verifyJwtAccessToken(credential.token)
            } catch (cause: OidcTokenRejectedException) {
                logger.trace("OpenID JWT access token authentication failed $cause")
                null
            }
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
            try {
                introspectOpaqueToken(credential.token)
            } catch (cause: OidcTokenRejectedException) {
                logger.trace("OpenID token introspection authentication failed $cause")
                null
            }
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

internal fun OidcProvider.createOauthFlow(): OAuth2Flow = withCapturedState {
    val loginPath = oidcRoutePath(oauthConfig.loginUri)
    val redirectPath = oidcRoutePath(oauthConfig.redirectUri)

    return oauth2(name) {
        client = this@createOauthFlow.client
        settings = oauthServerSettings()
        onUnauthorized = oauthFailureHandler
        this.loginPath = loginPath

        callback(redirectPath) callback@{ response ->
            val token = handleOAuthCallbackSuccess(response)
            oauthConfig.onAuthenticated(this, token)
        }
    }
}

internal fun OidcProvider.createOAuthSession(
    secureCookie: Boolean
): OAuth2SessionFlow<OidcToken.Id, OidcToken.Id> = withCapturedState {
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
                cookie.secure = secureCookie
                cookie.extensions["SameSite"] = "lax"
                sessionConfig.cookieConfigure?.invoke(this)
            }

            sessionCreator = { response -> handleOAuthCallbackSuccess(response) }

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

context(state: OidcProvider.State)
internal fun OidcProvider.oauthServerSettings(): OAuthServerSettings.OAuth2ServerSettings {
    return OAuthServerSettings.OAuth2ServerSettings(
        name = name,
        authorizeUrl = state.metadata.authorizationEndpoint,
        accessTokenUrl = state.metadata.tokenEndpoint,
        requestMethod = HttpMethod.Post,
        clientId = oauthConfig.clientId,
        clientSecret = oauthConfig.clientSecret,
        accessTokenRequiresBasicAuth = useBasicTokenEndpointAuth(),
        defaultScopes = oauthConfig.scopes,
        extraAuthParameters = oauthConfig.resourceIndicators.map { "resource" to it },
        extraTokenParameters = oauthConfig.resourceIndicators.map { "resource" to it },
        authorizeUrlInterceptor = authorize@{ request ->
            val state = parameters[OAuth2RequestParameters.State]
            val transaction = state?.let {
                request.call.readAuthorizationTransaction(stateCookieName, stateCodec, it)
            } ?: return@authorize
            parameters.append("nonce", transaction.nonce)
            oauthConfig.codeChallengeMethod?.let { method ->
                parameters.append("code_challenge", transaction.codeChallenge())
                parameters.append("code_challenge_method", method.name)
            }
        },
        verifyState = { call, state ->
            withCapturedState { call.validateAuthorizationResponseIssuer() }
            state != null && call.readAuthorizationTransaction(stateCookieName, stateCodec, state) != null
        },
        extraTokenParametersProvider = provider@{ call, callback ->
            if (oauthConfig.codeChallengeMethod == null) {
                return@provider emptyList()
            }
            val transaction = call.readAuthorizationTransaction(stateCookieName, stateCodec, callback.state)
            transaction?.let { listOf("code_verifier" to it.codeVerifier) }.orEmpty()
        },
        onStateCreated = { call, state ->
            val method = oauthConfig.codeChallengeMethod ?: CodeChallengeMethod.S256
            call.createAuthorizationTransaction(stateCookieName, stateCodec, method, state)
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
