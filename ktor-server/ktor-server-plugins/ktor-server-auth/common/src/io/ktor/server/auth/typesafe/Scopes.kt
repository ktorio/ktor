/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmName

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
@ExperimentalKtorApi
@KtorDsl
public class SessionAuthenticatedContext<S : Any, P : Any> @PublishedApi internal constructor(
    defaultContext: DefaultAuthenticatedContext<P>,
    private val sessionKey: AttributeKey<S>,
    private val sessionProviderName: String,
) : AuthenticatedContext<P> by defaultContext {
    /**
     * Returns the session value that passed authentication for [context].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.session)
     */
    public fun session(context: RoutingContext): S {
        return checkNotNull(context.call.attributes.getOrNull(sessionKey)) {
            "Session not found. This should not happen inside a session authenticateWith block."
        }
    }

    /**
     * Sets a new session value for [context].
     *
     * The captured route session is updated as well, so subsequent reads of [session] in the same handler see [value].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.setSession)
     */
    public fun setSession(context: RoutingContext, value: S) {
        context.call.sessions.set(sessionProviderName, value)
        context.call.attributes.put(sessionKey, value)
    }

    /**
     * Clears the authenticated session for [context].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.clearSession)
     */
    public fun clearSession(context: RoutingContext) {
        context.call.sessions.clear(sessionProviderName)
        context.call.attributes.remove(sessionKey)
    }

    /**
     * Replaces the authenticated session with the value returned by [transform].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticatedContext.updateSession)
     *
     * @return the updated session value.
     */
    public fun updateSession(context: RoutingContext, transform: (S) -> S): S {
        val updated = transform(session(context))
        setSession(context, updated)
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
 * Authenticated session captured for the current session-protected typed route.
 *
 * Assigning this property updates the stored session and the value exposed in the current route handler.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
@ExperimentalKtorApi
context(auth: SessionAuthenticatedContext<S, *>)
public var <S : Any> RoutingContext.session: S
    get() = auth.session(context = this)
    set(value) {
        auth.setSession(context = this, value)
    }

/**
 * Sets the authenticated session for the current session-protected typed route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.setSession)
 */
@ExperimentalKtorApi
@JvmName("setAuthenticatedSession")
context(auth: SessionAuthenticatedContext<S, *>)
public fun <S : Any> RoutingContext.setSession(value: S) {
    auth.setSession(context = this, value)
}

/**
 * Replaces the authenticated session with the value returned by [transform].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.updateSession)
 *
 * @return the updated session value.
 */
@ExperimentalKtorApi
context(auth: SessionAuthenticatedContext<S, *>)
public fun <S : Any> RoutingContext.updateSession(transform: (S) -> S): S {
    return auth.updateSession(context = this, transform)
}

/**
 * Clears the authenticated session for the current session-protected typed route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.clearSession)
 */
@ExperimentalKtorApi
context(auth: SessionAuthenticatedContext<S, *>)
public fun <S : Any> RoutingContext.clearSession() {
    auth.clearSession(context = this)
}

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
