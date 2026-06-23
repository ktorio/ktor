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
                val principal = verifyAccessToken(credential.token)
                transformPrincipal(principal)
            }.onFailure { cause ->
                logger.trace("OpenID access token authentication failed $cause")
            }.getOrNull()
        }

        onUnauthorized = {
            val challenge = HttpAuthHeader.Parameterized(AuthScheme.Bearer, parameters = emptyMap())
            call.respond(UnauthorizedResponse(challenge))
        }
    }
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
