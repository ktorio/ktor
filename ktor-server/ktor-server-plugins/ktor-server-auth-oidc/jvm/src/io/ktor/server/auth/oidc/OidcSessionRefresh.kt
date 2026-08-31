/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

context(ctx: RoutingContext)
internal suspend fun OidcProvider.refreshSessionIfNeeded(token: OidcToken.Id): OidcToken.Id? {
    if (managesRoute()) {
        return token
    }
    val now = Clock.System.now()
    val sessionConfig = checkNotNull(config.oauthConfig?.sessionConfig)
    return when (val strategy = sessionConfig.tokenRefreshStrategy) {
        is OidcTokenRefreshStrategy.Auto -> refreshSessionAutomatically(token, strategy.beforeExpiry, now)

        is OidcTokenRefreshStrategy.Disabled -> clearSessionIfExpired(token, now)

        is OidcTokenRefreshStrategy.Custom -> refreshSession(token, now) {
            with(strategy) {
                ctx.refresh(provider = this@refreshSessionIfNeeded, token, now)
            }
        }

        else -> error("Unsupported token refresh strategy ${strategy::class.simpleName}.")
    }
}

context(ctx: RoutingContext)
private fun OidcProvider.clearSessionIfExpired(token: OidcToken.Id, now: Instant): OidcToken.Id? {
    if (!token.isExpired(now)) {
        return token
    }
    logger.debug("OpenID Connect session expired. Removing session.")
    clearOidcSession()
    return null
}

context(ctx: RoutingContext)
private suspend fun OidcProvider.refreshSessionAutomatically(
    token: OidcToken.Id,
    beforeExpiry: Duration,
    now: Instant,
): OidcToken.Id? {
    if (!token.shouldRefresh(now, beforeExpiry)) {
        return token
    }

    val refreshTokenValue = token.refreshToken ?: run {
        logger.debug("OpenID Connect session has no refresh token")
        return clearSessionIfExpired(token, now)
    }

    return refreshSession(token, now) {
        refreshToken(refreshTokenValue).idToken?.takeIf { hasSameSubject(current = token, refreshed = it) }
    }
}

/**
 * OIDC Core 12.2 requires an ID token issued from a refresh request to keep the `sub` claim of the ID token
 * issued at the original authentication.
 */
internal fun OidcProvider.hasSameSubject(current: OidcToken.Id, refreshed: OidcToken.Id): Boolean {
    if (current.claims.subject == refreshed.claims.subject) {
        return true
    }
    logger.warn("OpenID Connect refresh returned an ID token for a different subject. Discarding it.")
    return false
}

context(ctx: RoutingContext)
private suspend fun OidcProvider.refreshSession(
    token: OidcToken.Id,
    now: Instant,
    refresh: suspend () -> OidcToken.Id?
): OidcToken.Id? {
    val newToken = try {
        refresh()
    } catch (cause: Exception) {
        clearSessionIfExpired(token, now)
        throw cause
    }
    return newToken ?: run {
        logger.debug("OpenID Connect session refresh did not return an ID token.")
        clearSessionIfExpired(token, now)
    }
}

context(ctx: RoutingContext)
private fun OidcProvider.clearOidcSession() {
    ctx.call.sessions.clear(oauthSessionFlow.session.name)
}

private fun OidcToken.Id.isExpired(now: Instant): Boolean =
    shouldRefresh(now, beforeExpiry = Duration.ZERO)

private fun OidcToken.Id.shouldRefresh(now: Instant, beforeExpiry: Duration): Boolean =
    claims.expiresAt?.let { it <= now + beforeExpiry } ?: false

context(ctx: RoutingContext)
private fun OidcProvider.managesRoute(): Boolean {
    val path = ctx.call.request.path()
    val isManaged = path == oauthConfig.refreshPath || path == oauthConfig.logoutPath
    return ctx.call.request.httpMethod == HttpMethod.Post && isManaged
}
