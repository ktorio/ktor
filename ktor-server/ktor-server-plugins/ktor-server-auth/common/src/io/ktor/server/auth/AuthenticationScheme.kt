/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class)

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.InternalAPI
import kotlin.reflect.KClass

/**
 * Handles an authentication failure for a typed authentication scheme.
 *
 * The handler receives the current [RoutingContext] and the [AuthenticationFailedCause] for the failed authentication
 * attempt.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.UnauthorizedHandler)
 */
public typealias UnauthorizedHandler = suspend RoutingContext.(AuthenticationFailedCause) -> Unit

private val RegisteredSchemesKey = AttributeKey<MutableMap<String, Any>>("TypesafeAuthRegisteredSchemes")

/**
 * The scheme is registered lazily when it is first used by [authenticateWith]. Reusing the same scheme instance in
 * another typed route reuses the existing application registration. Creating a different scheme instance with the same
 * name fails fast because scheme names are application-wide identifiers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticationScheme)
 *
 * @param P the principal type produced by this scheme.
 * @property name name that identifies this authentication scheme.
 * @property onUnauthorized default failure handler for routes that use the scheme. A route-level handler passed to
 * [authenticateWith] overrides this value.
 */
@SubclassOptInRequired
@ExperimentalKtorApi
public open class AuthenticationScheme<P : Any, C : AuthenticatedContext<P>> @PublishedApi internal constructor(
    @PublishedApi
    internal val provider: AuthenticationProvider,
    internal val principalType: KClass<P>,
    public val onUnauthorized: UnauthorizedHandler?,
    internal val anonymousFactory: (suspend RoutingContext.() -> P)?,
    @PublishedApi
    internal val contextFactory: (AuthenticatedContext<P>) -> C
) {
    public val name: String = checkNotNull(provider.name) {
        "Typed authentication schemes require a named AuthenticationProvider"
    }

    internal val principalKey = AttributeKey<P>("TypesafeAuth:$name:Principal", TypeInfo(principalType))

    private fun Application.registerSchemeIfNeeded() {
        val registered = attributes.computeIfAbsent(RegisteredSchemesKey) { mutableMapOf() }
        val existing = registered[name]
        if (existing != null) {
            require(existing === this@AuthenticationScheme) {
                "Typed authentication scheme `$name` is already registered"
            }
            return
        }
        registered[name] = this@AuthenticationScheme
        authentication { register(provider) }
    }

    internal open fun preinstallAt(route: Route) = route.application.registerSchemeIfNeeded()

    internal suspend fun AuthenticationContext.resolvePrincipal(): P? {
        val principal = principal(provider = name, klass = principalType)
        return when {
            principal != null -> principal
            failedWithNoCredentials() -> anonymousFactory?.invoke(call.toRoutingContext())
            else -> null
        }
    }

    internal fun createContext(): C =
        contextFactory(AuthenticatedContext(principalKey))

    internal fun requireOptionalCompatible(isOptional: Boolean) {
        require(!isOptional || anonymousFactory == null) {
            "authenticateWithOptional cannot be used with orAnonymous schemes. " +
                "Use authenticateWith with orAnonymous instead."
        }
    }

    public companion object {
        /**
         * Creates a [AuthenticationScheme] that exposes the authenticated principal through [AuthenticatedContext].
         *
         * Typed provider builders use this helper when they do not need a custom route context.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticationScheme.from)
         *
         * @param provider named provider implementation that authenticates requests for this scheme.
         * @param onUnauthorized default failure handler for routes that use the scheme.
         */
        @InternalAPI
        public inline fun <reified P : Any> from(
            provider: AuthenticationProvider,
            noinline onUnauthorized: UnauthorizedHandler?,
            noinline fallback: (AnonymousFactory<P>)? = null,
        ): SimpleAuthenticationScheme<P> {
            return AuthenticationScheme(
                provider = provider,
                principalType = P::class,
                onUnauthorized = onUnauthorized,
                anonymousFactory = fallback,
                contextFactory = { it }
            )
        }
    }
}

/**
 * Creates an anonymous principal when a request has no credentials.
 *
 * Used by [orAnonymous] to supply a fallback principal for optional authentication flows.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AnonymousFactory)
 */
public typealias AnonymousFactory<P> = suspend RoutingContext.() -> P

/**
 * Typed authentication scheme that exposes only [AuthenticatedContext].
 *
 * Most built-in providers such as [basic], [bearer], and [jwt] return this scheme type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SimpleAuthenticationScheme)
 */
public typealias SimpleAuthenticationScheme<P> = AuthenticationScheme<P, AuthenticatedContext<P>>

/**
 * Returns a scheme that accepts anonymous requests when no credentials are provided.
 *
 * Requests without credentials receive the principal produced by [fallback]. Requests with invalid credentials still
 * fail authentication. Use the returned scheme with [authenticateWith] and read
 * [ApplicationCall.principal] inside the route block.
 *
 * Do not combine the returned scheme with [authenticateWithOptional]. Use [authenticateWith] instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.orAnonymous)
 *
 * @param CP common principal type shared by authenticated and anonymous principals.
 * @param P authenticated principal type produced when credentials are valid.
 * @param AP authenticated principal type accepted by this scheme before widening to [CP].
 * @param fallback creates the anonymous principal when no credentials are present.
 * @return a scheme that authenticates valid credentials or falls back to an anonymous principal.
 */
@ExperimentalKtorApi
@OptIn(InternalAPI::class)
public inline fun <reified CP : Any, P : AP, AP : CP> AuthenticationScheme<P, AuthenticatedContext<P>>.orAnonymous(
    noinline fallback: AnonymousFactory<AP>
): AuthenticationScheme<CP, AuthenticatedContext<CP>> =
    AuthenticationScheme.from(provider, onUnauthorized, fallback)

internal fun AuthenticationContext.lastFailureOrNoCredentials(): AuthenticationFailedCause =
    challenge.register.lastOrNull()?.first ?: allFailures.lastOrNull() ?: AuthenticationFailedCause.NoCredentials

internal fun AuthenticationContext.failedWithNoCredentials(): Boolean =
    lastFailureOrNoCredentials() is AuthenticationFailedCause.NoCredentials
