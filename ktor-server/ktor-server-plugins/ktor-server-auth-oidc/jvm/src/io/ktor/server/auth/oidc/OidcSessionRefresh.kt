/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

context(ctx: RoutingContext)
internal suspend fun <P : Any> OidcProvider<P>.refreshSessionIfNeeded(token: OidcToken.Id): OidcToken.Id? {
    if (managesRoute()) {
        return token
    }
    val now = Clock.System.now()
    val sessionConfig = checkNotNull(config.sessionConfig)
    return when (val strategy = sessionConfig.tokenRefreshStrategy) {
        is OidcTokenRefreshStrategy.Auto -> refreshSessionAutomatically(token, strategy.beforeExpiry, now)
        is OidcTokenRefreshStrategy.Disabled -> keepSessionIfNotExpired(token, now)
        is OidcTokenRefreshStrategy.Custom -> refreshSession { strategy.refresh(provider = this, token) }
    }
}

context(ctx: RoutingContext)
private fun OidcProvider<*>.keepSessionIfNotExpired(token: OidcToken.Id, now: Instant): OidcToken.Id? {
    if (!token.isExpired(now)) {
        return token
    }
    logger.debug("OpenID Connect session expired")
    clearOidcSession()
    return null
}

context(ctx: RoutingContext)
private suspend fun <P : Any> OidcProvider<P>.refreshSessionAutomatically(
    token: OidcToken.Id,
    beforeExpiry: Duration,
    now: Instant,
): OidcToken.Id? {
    if (!token.shouldRefresh(now, beforeExpiry)) {
        return token
    }

    val refreshTokenValue = token.refreshToken ?: run {
        logger.debug("OpenID Connect session has no refresh token")
        clearOidcSession()
        return null
    }

    return refreshSession { refreshToken(refreshTokenValue).idToken }
}

context(ctx: RoutingContext)
private suspend fun OidcProvider<*>.refreshSession(
    refresh: suspend () -> OidcToken.Id?
): OidcToken.Id? {
    val newToken = try {
        refresh()
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        logger.debug("OpenID Connect session refresh failed: {}", cause.message)
        clearOidcSession()
        return null
    }
    return newToken ?: run {
        logger.debug("OpenID Connect session refresh did not return an ID token")
        clearOidcSession()
        return@run null
    }
}

private fun OidcToken.Id.isExpired(now: Instant): Boolean =
    shouldRefresh(now, beforeExpiry = Duration.ZERO)

private fun OidcToken.Id.shouldRefresh(now: Instant, beforeExpiry: Duration): Boolean =
    claims.expiresAt?.let { it <= now + beforeExpiry } ?: false

context(ctx: RoutingContext)
private fun OidcProvider<*>.clearOidcSession() {
    ctx.call.sessions.clear(oauthSessionFlow.sessions.name)
}

context(ctx: RoutingContext)
private fun OidcProvider<*>.managesRoute(): Boolean {
    val path = ctx.call.request.path()
    val isManaged = path == sessionRefreshPath || path == sessionLogoutPath
    return ctx.call.request.httpMethod == HttpMethod.Post && isManaged
}
