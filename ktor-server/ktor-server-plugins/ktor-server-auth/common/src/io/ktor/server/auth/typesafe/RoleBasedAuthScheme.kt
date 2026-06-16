/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Represents a role that can be required by a typed authentication route.
 *
 * Implement this interface on an enum or another role type used by your application.
 *
 * ```kotlin
 * enum class Role : AuthRole {
 *     User, Admin, Moderator
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthRole)
 */
public interface AuthRole {
    /**
     * Name of this role.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthRole.name)
     */
    public val name: String
}

/**
 * Creates a role-based scheme from this typed authentication scheme.
 *
 * Use this extension to add authorization on top of an existing typed authentication scheme.
 * After the base scheme authenticates a request, [resolveRoles] maps the principal to the roles held by that principal.
 * Route handlers then declare which roles are required via [Route.authenticateWith].
 *
 * Authentication and authorization fail separately:
 * - The base scheme handles missing or invalid credentials.
 * - Authenticated principals that lack any required role invoke [onForbidden] (HTTP 403 by default).
 *
 * Inside role-protected routes, use [ApplicationCall.principal] and [ApplicationCall.roles].
 *
 * ```kotlin
 * enum class Role : AuthRole {
 *     User, Admin
 * }
 *
 * val userAuth = basic<User>("users") {
 *     validate { credentials -> findUser(credentials) }
 * }
 *
 * val roleAuth = userAuth.withRoles { user ->
 *     if (user.isAdmin) setOf(Role.Admin, Role.User) else setOf(Role.User)
 * }
 *
 * routing {
 *     authenticateWith(roleAuth, roles = setOf(Role.Admin)) {
 *         get("/admin") {
 *             call.respondText("${call.principal.name}:${call.roles.joinToString { it.name }}")
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
 * [HttpStatusCode.Forbidden]. A route-level [ForbiddenHandler] passed to [Route.authenticateWith] overrides this
 * handler for that route.
 * @param resolveRoles maps the authenticated principal to the roles available for authorization.
 * The lambda receiver is the current [RoutingContext].
 * @return a [RoleBasedAuthScheme] that performs role checks after authentication.
 */
@ExperimentalKtorApi
public fun <P : Any, R : AuthRole> DefaultAuthScheme<P, *>.withRoles(
    onForbidden: suspend RoutingContext.(Set<R>) -> Unit = { _ -> call.respond(HttpStatusCode.Forbidden) },
    resolveRoles: suspend RoutingContext.(P) -> Set<R>
): RoleBasedAuthScheme<P, R> {
    return RoleBasedAuthScheme(
        base = this,
        principalType = principalType,
        defaultOnForbidden = onForbidden,
        resolveRoles = resolveRoles
    )
}

/**
 * Typed authentication scheme that checks resolved roles after authentication succeeds.
 *
 * Routes protected by this scheme receive a [RoleBasedContext] with [ApplicationCall.principal] and
 * [ApplicationCall.roles].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedAuthScheme)
 *
 * @param P the principal type.
 * @param R the role type.
 * @property base authentication scheme that provides the principal.
 */
public class RoleBasedAuthScheme<P : Any, R : AuthRole> internal constructor(
    public val base: DefaultAuthScheme<P, *>,
    internal val principalType: KClass<P>,
    private val defaultOnForbidden: ForbiddenHandler<R>,
    private val resolveRoles: suspend RoutingContext.(P) -> Set<R>
) : AuthScheme<P, RoleBasedContext<P, R>> {
    private val rolesKey: AttributeKey<Set<R>> = AttributeKey("TypesafeAuth:${base.name}:Roles")

    override val name: String = base.name

    override fun createAuthenticatedContext(route: Route): RoleBasedContext<P, R> =
        RoleBasedContext(base.principalKey, rolesKey)

    internal fun install(
        route: Route,
        roles: Set<R>,
        onUnauthorized: UnauthorizedHandler?,
        onForbidden: ForbiddenHandler<R>?
    ): RoleBasedContext<P, R> {
        base.install(
            route = route,
            kind = "Roles",
            onUnauthorized = onUnauthorized,
            onAccepted = { validateRoles(requiredRoles = roles, onForbidden) }
        )
        return createAuthenticatedContext(route)
    }

    internal suspend fun RoutingContext.validateRoles(requiredRoles: Set<R>, onForbidden: ForbiddenHandler<R>?) {
        if (requiredRoles.isEmpty()) {
            LOGGER.warn("Skipping role-based authentication because no roles are required")
            return
        }

        val principal = base.principalFrom(ctx = call.authentication)
            ?: return // Typed interceptor already handled unauthorized

        val resolvedRoles = resolveRoles(principal)
        when {
            resolvedRoles.containsAll(requiredRoles) -> call.attributes.put(rolesKey, resolvedRoles)
            onForbidden != null -> onForbidden(requiredRoles)
            else -> defaultOnForbidden(requiredRoles)
        }
    }
}
