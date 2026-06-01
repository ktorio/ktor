/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
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
     * Returns the principal captured for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext.principal)
     */
    public fun principal(call: ApplicationCall): P
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
    override fun principal(call: ApplicationCall): P {
        return checkNotNull(call.attributes.getOrNull(principalKey)) {
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
     * Returns the roles resolved for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedContext.roles)
     */
    public fun roles(call: ApplicationCall): Set<R> {
        return call.attributes[rolesKey]
    }
}

/**
 * Typed authentication context that exposes a stored session and the principal derived from it.
 *
 * @param S the stored session type.
 * @param P the principal type.
 */
@ExperimentalKtorApi
public interface SessionAuthenticatedContext<S : Any, P : Any> : AuthenticatedContext<P> {

    /**
     * Returns the session value captured for [call].
     */
    public fun getSession(call: ApplicationCall): S

    /**
     * Stores [value] as the current session for [call].
     */
    public fun setSession(call: ApplicationCall, value: S)

    /**
     * Replaces the current session for [call] with the value returned by [transform].
     *
     * @return the updated session value.
     */
    public fun updateSession(call: ApplicationCall, transform: (S) -> S): S

    /**
     * Clears the current session for [call].
     */
    public fun clearSession(call: ApplicationCall)
}

/**
 * Typed authentication context used by Session authentication.
 *
 * The context exposes the authenticated [principal], the session value that passed authentication, and helpers to
 * update or clear that session in a type-safe way.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext)
 *
 * @param S the stored session type.
 * @param P the principal type.
 */
@KtorDsl
@ExperimentalKtorApi
public class DefaultSessionAuthenticatedContext<S : Any, P : Any> @PublishedApi internal constructor(
    base: DefaultAuthenticatedContext<P>,
    private val sessionKey: AttributeKey<S>,
    private val sessionProviderName: String,
) : SessionAuthenticatedContext<S, P>, AuthenticatedContext<P> by base {
    /**
     * Returns the session value that passed authentication for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.session)
     */
    override fun getSession(call: ApplicationCall): S {
        return checkNotNull(call.attributes.getOrNull(sessionKey)) {
            "Session not found. This should not happen inside a session authenticateWith block."
        }
    }

    /**
     * Sets a new session value for [call].
     *
     * The captured route session is updated as well, so later reads of [getSession] in the same handler see [value].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.setSession)
     */
    override fun setSession(call: ApplicationCall, value: S) {
        call.sessions.set(sessionProviderName, value)
        call.attributes.put(sessionKey, value)
    }

    /**
     * Clears the authenticated session for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.clearSession)
     */
    override fun clearSession(call: ApplicationCall) {
        call.sessions.clear(sessionProviderName)
        call.attributes.remove(sessionKey)
    }

    /**
     * Replaces the authenticated session with the value returned by [transform].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.updateSession)
     *
     * @return the updated session value.
     */
    override fun updateSession(call: ApplicationCall, transform: (S) -> S): S {
        val updated = transform(getSession(call))
        setSession(call, updated)
        return updated
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
    override fun principal(call: ApplicationCall): P? {
        return call.attributes.getOrNull(principalKey)
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
context(routingCtx: RoutingContext, authCtx: AuthenticatedContext<P>)
public val <P> principal: P
    get() = authCtx.principal(routingCtx.call)

/**
 * Authenticated session captured for the current session-protected typed route.
 *
 * Assigning this property updates the stored session and the value exposed in the current route handler.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
@ExperimentalKtorApi
context(routingCtx: RoutingContext, authCtx: SessionAuthenticatedContext<S, *>)
public var <S : Any> session: S
    get() = authCtx.getSession(routingCtx.call)
    set(value) {
        authCtx.setSession(routingCtx.call, value)
    }

/**
 * Replaces the authenticated session with the value returned by [transform].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.updateSession)
 *
 * @return the updated session value.
 */
@ExperimentalKtorApi
context(routingCtx: RoutingContext, authCtx: SessionAuthenticatedContext<S, *>)
public fun <S : Any> updateSession(transform: (S) -> S): S {
    return authCtx.updateSession(routingCtx.call, transform)
}

/**
 * Clears the authenticated session for the current session-protected typed route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.clearSession)
 */
@ExperimentalKtorApi
context(routingCtx: RoutingContext, authCtx: SessionAuthenticatedContext<S, *>)
public fun <S : Any> clearSession() {
    authCtx.clearSession(routingCtx.call)
}

/**
 * Roles resolved for the current role-protected typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith] with a [RoleBasedAuthScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.roles)
 */
@ExperimentalKtorApi
context(routingCtx: RoutingContext, authCtx: RoleBasedContext<*, R>)
public val <R : AuthRole> roles: Set<R>
    get() = authCtx.roles(routingCtx.call)
