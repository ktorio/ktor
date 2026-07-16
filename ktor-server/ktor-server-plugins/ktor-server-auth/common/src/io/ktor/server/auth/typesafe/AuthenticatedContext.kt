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
 * Marks route blocks where a non-null principal is required.
 *
 * Typed authentication extensions such as [io.ktor.server.application.ApplicationCall.principal] and
 * [io.ktor.server.application.ApplicationCall.session] require this context receiver. Optional routes use
 * [io.ktor.server.application.ApplicationCall.principalOrNull] instead.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RequiredContext)
 */
@ExperimentalKtorApi
public object RequiredContext

/**
 * Typed authentication context that exposes the route principal.
 *
 * Used by [authenticateWith] for schemes that do not define a custom context.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext)
 *
 * @param P the principal type available inside the route.
 */
@ExperimentalKtorApi
@SubclassOptInRequired
public open class AuthenticatedContext<P : Any> @PublishedApi internal constructor(
    internal val principalKey: AttributeKey<P>,
)

/**
 * Authenticated principal captured for the current typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith] and is typed by the scheme used to
 * protect the route. Unlike [io.ktor.server.auth.principal], it does not require an explicit type argument and is
 * only available inside typed authentication route blocks.
 *
 * Use [io.ktor.server.auth.principal] outside typed routes or when reading a principal from a specific provider by
 * name.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.principal)
 */
@ExperimentalKtorApi
context(authCtx: AuthenticatedContext<P>, _: RequiredContext)
public val <P : Any> ApplicationCall.principal: P
    get() = checkNotNull(attributes.getOrNull(authCtx.principalKey)) {
        "Principal not found. This should not happen inside an authenticateWith block."
    }

/**
 * Authenticated principal for the current typed route, or `null` when authentication is optional and no principal
 * was resolved.
 *
 * Use this property inside [authenticateWithOptional] route blocks. For required authentication, prefer
 * [io.ktor.server.application.ApplicationCall.principal].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.principalOrNull)
 */
@ExperimentalKtorApi
context(authCtx: AuthenticatedContext<P>)
public val <P : Any> ApplicationCall.principalOrNull: P?
    get() = attributes.getOrNull(authCtx.principalKey)

/**
 * Typed authentication context that exposes both the authenticated principal and resolved roles.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RolesContext)
 *
 * @param P the principal type.
 * @param R the role type.
 */
@ExperimentalKtorApi
public class RolesContext<P : Any, R : AuthenticationRole> internal constructor(
    private val key: AttributeKey<Set<R>>,
) {
    /**
     * Returns the roles resolved for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RolesContext.getRoles)
     */
    public fun getRoles(call: ApplicationCall): Set<R> =
        checkNotNull(call.attributes.getOrNull(key)) {
            "Roles not found. This should not happen inside an authenticateWith block"
        }
}

/**
 * Typed authentication context used by Session authentication.
 *
 * The context exposes the authenticated [io.ktor.server.application.ApplicationCall.principal], the session value
 * that passed authentication, and helpers to update or clear that session in a type-safe way.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext)
 *
 * @param S the stored session type.
 * @param P the principal type.
 */
@ExperimentalKtorApi
public class SessionContext<S : Any, P : Any>(
    principalKey: AttributeKey<P>,
    private val sessionKey: AttributeKey<S>,
    private val sessionProviderName: String,
) : AuthenticatedContext<P>(principalKey) {
    /**
     * Returns the session value that passed authentication for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext.getSession)
     */
    public fun getSession(call: ApplicationCall): S {
        return checkNotNull(call.attributes.getOrNull(sessionKey)) {
            "Session not found. This should not happen inside a session authenticateWith block."
        }
    }

    /**
     * Returns the session value that passed authentication for [call], or `null` when no session is stored.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext.getSessionOrNull)
     */
    public fun getSessionOrNull(call: ApplicationCall): S? =
        call.attributes.getOrNull(sessionKey)

    /**
     * Sets a new session value for [call].
     *
     * The captured route session is updated as well, so later reads of [getSession] in the same handler see [value].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext.setSession)
     */
    public fun setSession(call: ApplicationCall, value: S) {
        call.sessions.set(sessionProviderName, value)
        call.attributes.put(sessionKey, value)
    }

    /**
     * Clears the authenticated session for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext.clearSession)
     */
    public fun clearSession(call: ApplicationCall) {
        call.sessions.clear(sessionProviderName)
        call.attributes.remove(sessionKey)
    }

    /**
     * Replaces the authenticated session with the value returned by [transform].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext.updateSession)
     *
     * @return the updated session value.
     */
    public fun updateSession(call: ApplicationCall, transform: (S) -> S): S {
        val updated = transform(getSession(call))
        setSession(call, updated)
        return updated
    }
}

/**
 * Authenticated session captured for the current session-protected typed route.
 *
 * Assigning this property updates the stored session and the value exposed in the current route handler.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
@ExperimentalKtorApi
context(authCtx: SessionContext<S, *>, _: RequiredContext)
public var <S : Any> ApplicationCall.session: S
    get() = authCtx.getSession(call = this)
    set(value) {
        authCtx.setSession(call = this, value)
    }

/**
 * Authenticated session for the current session-protected typed route, or `null` when no session was resolved.
 *
 * Use this property inside [authenticateWithOptional] session route blocks. Unlike
 * [io.ktor.server.application.ApplicationCall.session], this property does not require [RequiredContext].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.sessionOrNull)
 */
@ExperimentalKtorApi
context(authCtx: SessionContext<S, *>)
public val <S : Any> ApplicationCall.sessionOrNull: S?
    get() = authCtx.getSessionOrNull(call = this)

/**
 * Replaces the authenticated session with the value returned by [transform].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.updateSession)
 *
 * @return the updated session value.
 */
@ExperimentalKtorApi
context(sessionCtx: SessionContext<S, *>, _: RequiredContext)
public fun <S : Any> ApplicationCall.updateSession(transform: (S) -> S): S {
    return sessionCtx.updateSession(call = this, transform)
}

/**
 * Clears the authenticated session for the current session-protected typed route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.clearSession)
 */
@ExperimentalKtorApi
context(sessionCtx: SessionContext<S, *>)
public fun <S : Any> ApplicationCall.clearSession() {
    sessionCtx.clearSession(call = this)
}

/**
 * Roles resolved for the current role-protected typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith] with a [AuthenticationSchemeWithRoles].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.roles)
 */
@ExperimentalKtorApi
context(rolesContext: RolesContext<P, R>, routingContext: RoutingContext)
public val <P : Any, R : AuthenticationRole> P.roles: Set<R>
    get() = rolesContext.getRoles(call = routingContext.call)
