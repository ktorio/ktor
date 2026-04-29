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
 * The handler receives the current [ApplicationCall] and the [AuthenticationFailedCause] for the failed
 * authentication attempt.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.UnauthorizedHandler)
 */
public typealias UnauthorizedHandler = suspend (ApplicationCall, AuthenticationFailedCause) -> Unit

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
@OptIn(ExperimentalKtorApi::class)
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

/**
 * Default [AuthScheme] implementation created by typed authentication builders.
 *
 * The scheme is registered lazily when it is used by [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthScheme)
 *
 * @param P the principal type produced by this scheme.
 * @param C the context type available inside authenticated routes.
 * @property name name that identifies this authentication scheme.
 */
@ExperimentalKtorApi
public open class DefaultAuthScheme<P : Any, C : AuthenticatedContext<P>>(
    override val name: String,
    internal val principalType: KClass<P>,
    internal val provider: AuthenticationProvider,
    internal val onUnauthorized: UnauthorizedHandler?,
    internal val contextFactory: (AuthenticatedContextConfig<P>) -> C
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
        return contextFactory(AuthenticatedContextConfig(principalKey))
    }

    internal fun install(route: Route, onUnauthorized: UnauthorizedHandler?) {
        val plugin = createTypedAuthPlugin(
            route = route,
            kind = "Scheme",
            onUnauthorized = onUnauthorized ?: this@DefaultAuthScheme.onUnauthorized,
        )
        route.install(plugin)
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
         * Creates a [DefaultAuthScheme] that exposes the authenticated principal through [DefaultAuthenticatedContext].
         *
         * Typed provider builders use this helper when they do not need a custom route context.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthScheme.withDefaultContext)
         *
         * @param name name that identifies the authentication scheme.
         * @param provider provider implementation that authenticates requests for this scheme.
         * @param onUnauthorized default failure handler for routes that use the scheme.
         */
        public inline fun <reified P : Any> withDefaultContext(
            name: String,
            provider: AuthenticationProvider,
            noinline onUnauthorized: UnauthorizedHandler?
        ): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
            return DefaultAuthScheme(
                name = name,
                principalType = P::class,
                provider = provider,
                onUnauthorized = onUnauthorized
            ) { config -> DefaultAuthenticatedContext(config.principalKey) }
        }
    }
}
