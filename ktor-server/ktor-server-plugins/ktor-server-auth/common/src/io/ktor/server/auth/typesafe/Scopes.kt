/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Provides typed access to an authenticated principal captured for a typed authentication route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext)
 *
 * @param P the principal type, or a nullable principal type for optional authentication.
 */
@ExperimentalKtorApi
@KtorDsl
public interface AuthenticatedContext<P> {
    /**
     * Returns the principal captured for [context].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext.principal)
     */
    public fun principal(context: RoutingContext): P
}

/**
 * Provides access to data needed to create a custom authenticated context.
 *
 * Pass this configuration to custom [AuthenticatedContext] implementations created by [DefaultAuthScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContextConfig)
 *
 * @param P the principal type produced by the scheme.
 */
@ExperimentalKtorApi
public class AuthenticatedContextConfig<P : Any> internal constructor(
    @PublishedApi internal val principalKey: AttributeKey<P>,
) {
    /**
     * Returns the non-null principal captured for [context].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContextConfig.principal)
     *
     * @throws IllegalStateException when called outside the route protected by the scheme.
     */
    public fun principal(context: RoutingContext): P {
        return checkNotNull(context.call.attributes.getOrNull(principalKey)) {
            "Principal not found. This should not happen inside an authenticateWith block."
        }
    }
}

/**
 * Default typed authentication context used by [authenticateWith].
 *
 * The context exposes the authenticated [principal] and is used by typed schemes that do not define a custom context.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.DefaultAuthenticatedContext)
 *
 * @param P the principal type available inside the route.
 */
@ExperimentalKtorApi
@KtorDsl
public open class DefaultAuthenticatedContext<P : Any> @PublishedApi internal constructor(
    @PublishedApi internal val principalKey: AttributeKey<P>,
) : AuthenticatedContext<P> {
    override fun principal(context: RoutingContext): P {
        return checkNotNull(context.call.attributes.getOrNull(principalKey)) {
            "Principal not found. This should not happen inside an authenticateWith block."
        }
    }
}

/**
 * Typed authentication context that exposes both the authenticated principal and resolved roles.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedContext)
 *
 * @param P the principal type.
 * @param R the role type.
 */
@ExperimentalKtorApi
@KtorDsl
public class RoleBasedContext<P : Any, R : AuthRole> internal constructor(
    principalKey: AttributeKey<P>,
    private val rolesKey: AttributeKey<Set<R>>,
) : DefaultAuthenticatedContext<P>(principalKey) {
    /**
     * Returns the roles resolved for [context].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedContext.roles)
     */
    public fun roles(context: RoutingContext): Set<R> {
        return context.call.attributes[rolesKey]
    }
}

/**
 * Typed authentication context used when authentication is optional.
 *
 * The [principal] is `null` when the request has no credentials.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OptionalAuthenticatedContext)
 *
 * @param P the principal type produced when authentication succeeds.
 */
@ExperimentalKtorApi
@KtorDsl
public class OptionalAuthenticatedContext<P : Any> internal constructor(
    private val principalKey: AttributeKey<P>,
) : AuthenticatedContext<P?> {
    override fun principal(context: RoutingContext): P? {
        return context.call.attributes.getOrNull(principalKey)
    }
}

/**
 * Returns the authenticated context available in the current typed route builder scope.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticatedContext)
 */
@ExperimentalKtorApi
context(auth: A)
public fun <P, A : AuthenticatedContext<P>> authenticatedContext(): A = auth

/**
 * Authenticated principal captured for the current typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.principal)
 */
@ExperimentalKtorApi
context(auth: AuthenticatedContext<P>)
public val <P> RoutingContext.principal: P
    get() = auth.principal(context = this)

/**
 * Roles resolved for the current role-protected typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith] with a [RoleBasedAuthScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.roles)
 */
@ExperimentalKtorApi
context(auth: RoleBasedContext<*, R>)
public val <R : AuthRole> RoutingContext.roles: Set<R>
    get() = auth.roles(context = this)
