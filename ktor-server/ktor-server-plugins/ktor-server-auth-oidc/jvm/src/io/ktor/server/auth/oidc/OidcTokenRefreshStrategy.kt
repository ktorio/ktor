/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Strategy used to refresh OpenID Connect browser sessions.
 *
 * @param P provider principal type exposed to route handlers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.oidc.OidcTokenRefreshStrategy)
 */
public sealed interface OidcTokenRefreshStrategy<in P : Any> {
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
    ) : OidcTokenRefreshStrategy<Any> {
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
    public object Disabled : OidcTokenRefreshStrategy<Any>

    /**
     * Custom session refresh policy.
     *
     * The callback is invoked for every session-authenticated user request.
     *
     * @param P provider principal type exposed to route handlers.
     */
    public fun interface Custom<P : Any> : OidcTokenRefreshStrategy<P> {
        /**
         * Returns the effective ID-token session for this request.
         *
         * Return the current [token] to keep the session unchanged, a new [OidcToken.Id] to update stored
         * session material, or `null` to invalidate the session.
         *
         * @param provider provider that authenticated the session.
         * @param token current ID-token session.
         */
        public suspend fun refresh(provider: OidcProvider<P>, token: OidcToken.Id): OidcToken.Id?
    }
}
