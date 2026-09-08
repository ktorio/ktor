/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalTime::class)

package io.ktor.server.auth.oidc

import io.ktor.server.routing.RoutingContext
import io.ktor.util.annotations.InternalKtorSubclassing
import io.ktor.utils.io.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Strategy used to refresh OpenID Connect browser sessions.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcTokenRefreshStrategy)
 */
@ExperimentalKtorApi
@SubclassOptInRequired(InternalKtorSubclassing::class)
public interface OidcTokenRefreshStrategy {
    /**
     * Refreshes the session automatically before it expires.
     *
     * Refresh timing uses [OidcToken.Id.claims] [io.ktor.server.auth.oidc.TokenClaims.expiresAt].
     * When the ID token has no `exp` claim, auto-refresh never triggers.
     *
     * @property beforeExpiry how long before ID-token expiration the plugin should refresh the session.
     */
    public class Auto(
        public val beforeExpiry: Duration = 30.seconds,
    ) : OidcTokenRefreshStrategy {
        init {
            require(beforeExpiry.isFinite() && !beforeExpiry.isNegative()) {
                "beforeExpiry must be finite and non-negative"
            }
        }
    }

    /**
     * Disables automatic refresh.
     *
     * Expired ID-token sessions are still rejected on user routes. Expiry uses
     * [OidcToken.Id.claims] [io.ktor.server.auth.oidc.TokenClaims.expiresAt]; when the ID token has no
     * `exp` claim, the session is never treated as expired.
     */
    public object Disabled : OidcTokenRefreshStrategy

    /**
     * Custom session refresh policy.
     *
     * The callback is invoked for every session-authenticated user request.
     */
    public fun interface Custom : OidcTokenRefreshStrategy {
        /**
         * Returns the effective ID-token session for this request.
         *
         * Return the current [token] to keep the session unchanged, a new [OidcToken.Id] to update stored
         * session material, or `null` when no refreshed token is available. On `null` (or a thrown exception),
         * the session is kept while the current token is still valid and cleared once it has expired. To end
         * a session immediately, clear it with the `Sessions` plugin instead.
         *
         * @param token current ID-token session.
         * @param now request time captured before the strategy runs; use this instead of reading the clock again.
         */
        public suspend fun RoutingContext.refresh(
            provider: OidcProvider,
            token: OidcToken.Id,
            now: Instant
        ): OidcToken.Id?
    }
}
