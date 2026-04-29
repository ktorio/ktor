/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.util.*
import io.ktor.util.collections.*
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
@ExperimentalKtorApi
public interface AuthRole {
    /**
     * Name of this role.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthRole.name)
     */
    public val name: String
}

/**
 * Defines how roles resolved for a principal are cached.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RolesCacheStrategy)
 *
 * @param P the principal type.
 * @param ID the cache key type.
 */
@ExperimentalKtorApi
public sealed class RolesCacheStrategy<in P : Any, in ID : Any> {

    /**
     * Resolves roles on every call without caching.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RolesCacheStrategy.PerCall)
     */
    public object PerCall : RolesCacheStrategy<Any, Any>()

    /**
     * Caches resolved roles per principal identity key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RolesCacheStrategy.PerKey)
     *
     * @param P the principal type.
     * @param ID the cache key type.
     * @property extractKey extracts the cache key from the authenticated principal.
     */
    public class PerKey<P : Any, ID : Any>(
        public val extractKey: (P) -> ID
    ) : RolesCacheStrategy<P, ID>()
}

/**
 * Creates a role-based scheme from this typed authentication scheme.
 *
 * Roles are resolved for each authenticated call. Use the returned scheme with [authenticateWith] and pass the roles
 * required by a route.
 *
 * ```kotlin
 * val adminAuth = userAuth.withRoles { user -> user.roles }
 *
 * routing {
 *     authenticateWith(adminAuth, roles = setOf(Role.Admin)) {
 *         get("/admin") {
 *             call.respondText(principal.name)
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.withRoles)
 *
 * @param onForbidden default handler invoked when a principal does not have the required roles.
 * @param resolve resolves roles for the authenticated principal.
 * @return a typed authentication scheme that also exposes [roles].
 */
@ExperimentalKtorApi
public fun <P : Any, R : AuthRole> DefaultAuthScheme<P, *>.withRoles(
    onForbidden: suspend (ApplicationCall, Set<R>) -> Unit = { call, _ -> call.respond(HttpStatusCode.Forbidden) },
    resolve: suspend ApplicationCall.(P) -> Set<R>
): RoleBasedAuthScheme<P, R, Unit> =
    withRoles(
        cacheStrategy = RolesCacheStrategy.PerCall,
        onForbidden = onForbidden,
        resolve = resolve
    )

/**
 * Creates a role-based scheme from this typed authentication scheme with the specified [cacheStrategy].
 *
 * Use [RolesCacheStrategy.PerKey] when role lookup is expensive and roles can be reused for the same principal key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.withRoles)
 *
 * @param cacheStrategy strategy used to cache resolved roles.
 * @param onForbidden default handler invoked when a principal does not have the required roles.
 * @param resolve resolves roles for the authenticated principal.
 * @return a typed authentication scheme that also exposes [roles].
 */
@ExperimentalKtorApi
public fun <P : Any, R : AuthRole, ID : Any> DefaultAuthScheme<P, *>.withRoles(
    cacheStrategy: RolesCacheStrategy<P, ID>,
    onForbidden: suspend (ApplicationCall, Set<R>) -> Unit = { call, _ -> call.respond(HttpStatusCode.Forbidden) },
    resolve: suspend ApplicationCall.(P) -> Set<R>
): RoleBasedAuthScheme<P, R, ID> {
    return RoleBasedAuthScheme(
        base = this,
        principalType = principalType,
        onForbidden = onForbidden,
        cacheStrategy = cacheStrategy,
        rolesResolver = resolve
    )
}

/**
 * Typed authentication scheme that checks resolved roles after authentication succeeds.
 *
 * Routes protected by this scheme receive a [RoleBasedContext] with both [principal] and [roles].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.RoleBasedAuthScheme)
 *
 * @param P the principal type.
 * @param R the role type.
 * @param ID the role cache key type.
 * @property base authentication scheme that provides the principal.
 */
@ExperimentalKtorApi
public class RoleBasedAuthScheme<P : Any, R : AuthRole, ID : Any>(
    public val base: DefaultAuthScheme<P, *>,
    internal val principalType: KClass<P>,
    private val onForbidden: suspend (ApplicationCall, Set<R>) -> Unit,
    private val cacheStrategy: RolesCacheStrategy<P, ID>,
    private val rolesResolver: suspend ApplicationCall.(P) -> Set<R>
) : AuthScheme<P, RoleBasedContext<P, R>> {
    private val cache by lazy { ConcurrentMap<ID, Set<R>>() }
    private val rolesKey: AttributeKey<Set<R>> = AttributeKey("TypesafeAuth:${base.name}:Roles")

    override val name: String = base.name

    override fun createAuthenticatedContext(route: Route): RoleBasedContext<P, R> =
        RoleBasedContext(base.principalKey, rolesKey)

    internal suspend fun validateRoles(
        call: ApplicationCall,
        requiredRoles: Set<R>,
        onForbidden: ForbiddenHandler?
    ) {
        if (requiredRoles.isEmpty()) {
            LOGGER.debug("Skipping role-based authentication because no roles are required")
            return
        }

        val principal = base.principalFrom(ctx = call.authentication)
            ?: return // Typed interceptor already handled unauthorized

        val resolvedRoles = call.resolveRolesFor(principal)
        when {
            resolvedRoles.containsAll(requiredRoles) -> call.attributes.put(rolesKey, resolvedRoles)
            onForbidden != null -> onForbidden(call, requiredRoles)
            else -> this.onForbidden(call, requiredRoles)
        }
    }

    private suspend fun ApplicationCall.resolveRolesFor(principal: P): Set<R> =
        when (val strategy = cacheStrategy) {
            is RolesCacheStrategy.PerCall -> rolesResolver(principal)
            is RolesCacheStrategy.PerKey -> {
                val key = strategy.extractKey(principal)
                cache.getOrPut(key) { rolesResolver(principal) }
            }
        }
}
