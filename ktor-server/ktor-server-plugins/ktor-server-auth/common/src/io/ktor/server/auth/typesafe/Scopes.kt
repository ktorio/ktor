/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.*

/**
 * Provides typed access to an authenticated principal captured for a typed authentication route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext)
 *
 * @param P the principal type, or a nullable principal type for optional authentication.
 */
public interface AuthenticatedContext<P> {
    /**
     * Returns the principal captured for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedContext.principal)
     */
    public fun getPrincipal(call: ApplicationCall): P
}

/**
 * Typed authentication context that exposes the route principal.
 *
 * Used by [authenticateWith] for schemes that do not define a custom context.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.PrincipalContext)
 *
 * @param P the principal type available inside the route.
 */
public open class PrincipalContext<P : Any> @PublishedApi internal constructor(
    @PublishedApi internal val principalKey: AttributeKey<P>,
) : AuthenticatedContext<P> {
    override fun getPrincipal(call: ApplicationCall): P {
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
public class RoleBasedContext<P : Any, R : AuthRole> internal constructor(
    principalKey: AttributeKey<P>,
    private val rolesKey: AttributeKey<Set<R>>,
) : PrincipalContext<P>(principalKey) {
    /**
     * Returns the roles resolved for [call].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedContext.roles)
     */
    public fun getRoles(call: ApplicationCall): Set<R> = call.attributes[rolesKey]
}

/**
 * Typed authentication context used by Session authentication.
 *
 * The context exposes the authenticated [ApplicationCall.principal], the session value that passed authentication,
 * and helpers to update or clear that session in a type-safe way.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContext)
 *
 * @param S the stored session type.
 * @param P the principal type.
 */
public class SessionContext<S : Any, P : Any>(
    base: PrincipalContext<P>,
    private val sessionKey: AttributeKey<S>,
    private val sessionProviderName: String,
) : AuthenticatedContext<P> by base {
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
 * Typed authentication context used when authentication is optional.
 *
 * The [ApplicationCall.principal] is `null` when the request has no credentials.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OptionalPrincipalContext)
 *
 * @param P the principal type produced when authentication succeeds.
 */
public class OptionalPrincipalContext<P : Any> internal constructor(
    private val principalKey: AttributeKey<P>,
) : AuthenticatedContext<P?> {
    override fun getPrincipal(call: ApplicationCall): P? {
        return call.attributes.getOrNull(principalKey)
    }
}

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
context(authCtx: AuthenticatedContext<P>)
public val <P> ApplicationCall.principal: P
    get() = authCtx.getPrincipal(call = this)

/**
 * Authenticated session captured for the current session-protected typed route.
 *
 * Assigning this property updates the stored session and the value exposed in the current route handler.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 */
context(authCtx: SessionContext<S, *>)
public var <S : Any> ApplicationCall.session: S
    get() = authCtx.getSession(call = this)
    set(value) {
        authCtx.setSession(call = this, value)
    }

/**
 * Replaces the authenticated session with the value returned by [transform].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.updateSession)
 *
 * @return the updated session value.
 */
context(authCtx: SessionContext<S, *>)
public fun <S : Any> ApplicationCall.updateSession(transform: (S) -> S): S {
    return authCtx.updateSession(call = this, transform)
}

/**
 * Clears the authenticated session for the current session-protected typed route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.clearSession)
 */
context(authCtx: SessionContext<S, *>)
public fun <S : Any> ApplicationCall.clearSession() {
    authCtx.clearSession(call = this)
}

/**
 * Roles resolved for the current role-protected typed route.
 *
 * The property is available inside route handlers nested in [authenticateWith] with a [RoleBasedAuthScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.roles)
 */
context(authCtx: RoleBasedContext<*, R>)
public val <R : AuthRole> ApplicationCall.roles: Set<R>
    get() = authCtx.getRoles(call = this)
