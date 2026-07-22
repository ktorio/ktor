/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*

/**
 * Represents a role that can be required by a typed authentication route.
 *
 * Implement this interface on an enum or another role type used by your application.
 *
 * ```kotlin
 * enum class Role : AuthenticationRole {
 *     User, Admin, Moderator
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticationRole)
 */
@ExperimentalKtorApi
public interface AuthenticationRole {
    /**
     * Name of this role.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticationRole.name)
     */
    public val name: String
}

/**
 * Handles authorization failure for a role-protected typed route.
 *
 * The handler receives the current [RoutingContext] and the roles required by the route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.ForbiddenHandler)
 */
public typealias ForbiddenHandler<R> = suspend RoutingContext.(Set<R>) -> Unit

/**
 * Creates a role-based scheme from this typed authentication scheme.
 *
 * Use this extension to add authorization on top of an existing typed authentication scheme.
 * After the base scheme authenticates a request, [resolveRoles] maps the principal to the roles held by that principal.
 * Route handlers then declare which roles are required via [authenticateWith].
 *
 * Authentication and authorization fail separately:
 * - The base scheme handles missing or invalid credentials.
 * - Authenticated principals that lack any required role invoke [onForbidden] (HTTP 403 by default).
 *
 * Inside role-protected routes, use [io.ktor.server.application.ApplicationCall.principal] and [P.roles].
 *
 * ```kotlin
 * enum class Role : AuthenticationRole {
 *     User, Admin
 * }
 *
 * val userAuth = basic<User>("users") {
 *     validate { credentials -> findUser(credentials) }
 * }
 *
 * val roleAuth = userAuth.withRoles { user ->
 *     redis.getUserRoles(user.id) // suspend lookup from Redis or database
 * }
 *
 * routing {
 *     authenticateWith(roleAuth, roles = setOf(Role.Admin)) {
 *         get("/admin") {
 *             val user = call.principal
 *             call.respondText("${user.name}:${user.roles.joinToString { it.name }}")
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.withRoles)
 *
 * @param P the principal type produced by the base scheme.
 * @param R the role type used for authorization checks.
 * @param onForbidden handler invoked when authentication succeeds, but the principal does not have every role
 * required by the route. Receives the set of roles that the route demanded. Defaults to responding with
 * [HttpStatusCode.Forbidden]. A route-level [ForbiddenHandler] passed to [authenticateWith] overrides this
 * handler for that route.
 * @param resolveRoles suspend function that maps the authenticated principal to the roles available for
 * authorization. The receiver is the current [RoutingContext]; use it to load roles from Redis, a database, or
 * another external store.
 * @return a [AuthenticationSchemeWithRoles] that performs role checks after authentication.
 */
@ExperimentalKtorApi
public fun <P, R, C, S> S.withRoles(
    onForbidden: ForbiddenHandler<R> = { _ -> call.respond(HttpStatusCode.Forbidden) },
    resolveRoles: suspend RoutingContext.(P) -> Set<R>,
): AuthenticationSchemeWithRoles<P, R, C, S> where P : Any,
                                                   R : AuthenticationRole,
                                                   C : AuthenticatedContext<P>,
                                                   S : AuthenticationScheme<P, C> =
    AuthenticationSchemeWithRoles(base = this, onForbidden, resolveRoles)

/**
 * Typed authentication scheme that checks resolved roles after authentication succeeds.
 *
 * Routes protected by this scheme receive a [RolesContext] with [io.ktor.server.application.ApplicationCall.principal]
 * and [P.roles].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticationSchemeWithRoles)
 *
 * @param P the principal type.
 * @param R the role type.
 * @property base authentication scheme that provides the principal.
 * @property onForbidden default handler invoked when authentication succeeds but the principal lacks required roles.
 * A route-level [ForbiddenHandler] passed to [authenticateWith] overrides this handler.
 */
@ExperimentalKtorApi
public class AuthenticationSchemeWithRoles<P, R, C, B> internal constructor(
    public val base: B,
    public val onForbidden: ForbiddenHandler<R>,
    private val resolveRoles: suspend RoutingContext.(P) -> Set<R>,
) where P : Any,
          R : AuthenticationRole,
          C : AuthenticatedContext<P>,
          B : AuthenticationScheme<P, C> {
    private val rolesKey: AttributeKey<Set<R>> = AttributeKey("TypesafeAuth:${base.name}:Roles")

    internal fun createContext(): RolesContext<P, R> =
        RolesContext(rolesKey)

    context(routingContext: RoutingContext)
    internal suspend fun validateRoles(
        principal: P,
        requiredRoles: Set<R>?,
        onForbidden: ForbiddenHandler<R>?
    ) {
        val resolvedRoles = resolveRoles(routingContext, principal)
        val call = routingContext.call
        call.attributes.put(rolesKey, resolvedRoles)
        when {
            requiredRoles == null || resolvedRoles.containsAll(requiredRoles) -> return
            onForbidden != null -> onForbidden(routingContext, requiredRoles)
            else -> this.onForbidden(routingContext, requiredRoles)
        }
    }
}
