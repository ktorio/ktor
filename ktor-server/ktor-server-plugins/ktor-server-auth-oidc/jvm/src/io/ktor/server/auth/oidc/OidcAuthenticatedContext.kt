/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.oidc

import io.ktor.server.auth.typesafe.*
import io.ktor.utils.io.*

/**
 * Typed route context that exposes the [OidcProvider] that authenticated the current route.
 *
 * @param P provider principal type exposed to the route.
 */
public interface OidcProviderContext<P : Any> {
    /**
     * Returns the OpenID Connect provider associated with the current authenticated route.
     */
    public fun provider(): OidcProvider<P>
}

/**
 * OpenID Connect provider for the current authenticated route.
 *
 * The provider name is taken from the [OidcProviderContext] used by `authenticateWith`.
 */
@ExperimentalKtorApi
context(ctx: OidcProviderContext<P>)
public val <P : Any> provider: OidcProvider<P>
    get() = ctx.provider()

/**
 * Route context used by OpenID Connect Bearer authentication.
 *
 * It exposes the typed authenticated principal and provider-bound helpers inside
 * `authenticateWith(provider.bearer)` route bodies.
 *
 * @param P provider principal type exposed to the route.
 */
@KtorDsl
@OptIn(ExperimentalKtorApi::class)
public class OidcBearerContext<P : Any> internal constructor(
    default: DefaultAuthenticatedContext<P>,
    private val provider: OidcProvider<P>,
) : AuthenticatedContext<P> by default, OidcProviderContext<P> {

    override fun provider(): OidcProvider<P> = provider
}

/**
 * Route context used by OpenID Connect session authentication.
 *
 * It exposes the typed authenticated principal, the raw stored [OidcToken.Id] session, and provider-bound helpers
 * inside `authenticateWith(provider.sessions)` route bodies.
 *
 * @param P provider principal type exposed to the route.
 */
@KtorDsl
@OptIn(ExperimentalKtorApi::class)
public class OidcSessionsContext<P : Any> internal constructor(
    default: SessionAuthenticatedContext<OidcToken.Id, P>,
    private val provider: OidcProvider<P>,
) : SessionAuthenticatedContext<OidcToken.Id, P> by default, OidcProviderContext<P> {

    override fun provider(): OidcProvider<P> = provider
}

/**
 * Typed Bearer authentication scheme for an OpenID Connect provider.
 *
 * @param P provider principal type exposed to the route.
 */
@OptIn(ExperimentalKtorApi::class)
public typealias OidcBearerScheme<P> = DefaultAuthScheme<P, OidcBearerContext<P>>

/**
 * Typed session authentication scheme for an OpenID Connect provider.
 *
 * @param P provider principal type exposed to the route.
 */
@OptIn(ExperimentalKtorApi::class)
public typealias OidcSessionsScheme<P> = SessionAuthScheme<OidcToken.Id, P, OidcSessionsContext<P>>
