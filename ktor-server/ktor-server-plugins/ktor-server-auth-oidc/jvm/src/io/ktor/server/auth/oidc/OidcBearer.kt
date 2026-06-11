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
                val principal = verifyAccessToken(credential.token)
                transformPrincipal(principal)
            }.onFailure {
                logger.trace("OpenID access token authentication failed {}", it.message)
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
