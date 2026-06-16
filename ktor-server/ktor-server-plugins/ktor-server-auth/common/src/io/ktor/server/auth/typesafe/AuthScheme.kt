/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
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

/**
 * Represents a typed authentication scheme that can provide an authenticated route context.
 *
 * A scheme has a stable name and creates the context used by [authenticateWith] route bodies.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthScheme)
 *
 * @param P the principal type produced by this scheme.
 * @param C the context type available inside authenticated routes.
 */
public interface AuthScheme<P : Any, C : AuthenticatedContext<*>> {
    /**
     * Name that identifies this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthScheme.name)
     */
    public val name: String

    /**
     * Creates a context that exposes authentication data for [route].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthScheme.createAuthenticatedContext)
     *
     * @return the authenticated context used by route handlers.
     */
    public fun createAuthenticatedContext(route: Route): C
}

private val RegisteredSchemesKey = AttributeKey<MutableMap<String, Any>>("TypesafeAuthRegisteredSchemes")

/**
 * Default [AuthScheme] implementation created by typed authentication builders.
 *
 * The scheme is registered lazily when it is first used by [authenticateWith]. Reusing the same scheme instance in
 * another typed route reuses the existing application registration. Creating a different scheme instance with the same
 * name fails fast because scheme names are application-wide identifiers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthScheme)
 *
 * @param P the principal type produced by this scheme.
 * @param C the context type available inside authenticated routes.
 * @property name name that identifies this authentication scheme.
 */
public open class DefaultAuthScheme<P : Any, C : AuthenticatedContext<P>>(
    override val name: String,
    internal val principalType: KClass<P>,
    internal val provider: AuthenticationProvider,
    internal val onUnauthorized: UnauthorizedHandler?,
    internal val contextFactory: (PrincipalContext<P>) -> C
) : AuthScheme<P, C> {
    internal val principalKey = AttributeKey<P>("TypesafeAuth:$name:Principal", TypeInfo(principalType))

    /**
     * Registers this scheme in an [AuthenticationConfig].
     *
     * Typed route builders call this automatically when a scheme is used with [authenticateWith].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthScheme.setup)
     *
     * @param config authentication configuration where the scheme should be registered.
     */
    internal fun setup(config: AuthenticationConfig) {
        config.register(provider)
    }

    override fun createAuthenticatedContext(route: Route): C {
        return contextFactory(PrincipalContext(principalKey))
    }

    private fun Application.registerSchemeIfNeeded() {
        val registered = attributes.computeIfAbsent(RegisteredSchemesKey) { mutableMapOf() }
        val existing = registered[name]
        if (existing != null) {
            require(existing === this@DefaultAuthScheme) {
                "Typed authentication scheme `$name` is already registered"
            }
            return
        }
        registered[name] = this@DefaultAuthScheme
        authentication { setup(this) }
    }

    internal open fun preinstall(route: Route) {
        route.application.registerSchemeIfNeeded()
    }

    internal fun install(
        route: Route,
        onUnauthorized: UnauthorizedHandler? = null,
        kind: String = "Scheme",
        optional: Boolean = false,
        onAccepted: (suspend RoutingContext.() -> Unit)? = null
    ): C {
        preinstall(route)
        val plugin = createTypedAuthPlugin(
            route = route,
            kind = kind,
            onUnauthorized = onUnauthorized ?: this@DefaultAuthScheme.onUnauthorized,
            optional = optional,
            onAccepted = onAccepted,
        )
        route.install(plugin)
        return createAuthenticatedContext(route)
    }

    internal fun principalFrom(ctx: AuthenticationContext): P? {
        return ctx.principal(provider = name, klass = principalType)
    }

    internal fun capture(call: ApplicationCall, principal: Any) {
        @Suppress("UNCHECKED_CAST")
        call.attributes.put(principalKey, principal as P)
    }

    public companion object {
        /**
         * Creates a [DefaultAuthScheme] that exposes the authenticated principal through [PrincipalContext].
         *
         * Typed provider builders use this helper when they do not need a custom route context.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthScheme.withDefaultContext)
         *
         * @param name name that identifies the authentication scheme.
         * @param provider provider implementation that authenticates requests for this scheme.
         * @param onUnauthorized default failure handler for routes that use the scheme.
         */
        @InternalAPI
        public inline fun <reified P : Any> withDefaultContext(
            name: String,
            provider: AuthenticationProvider,
            noinline onUnauthorized: UnauthorizedHandler?
        ): DefaultAuthScheme<P, PrincipalContext<P>> {
            return DefaultAuthScheme(
                name = name,
                provider = provider,
                principalType = P::class,
                onUnauthorized = onUnauthorized,
                contextFactory = { it }
            )
        }
    }
}

public typealias DefaultAuthenticatedScheme<P> = DefaultAuthScheme<P, PrincipalContext<P>>
