/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class, ExperimentalKtorApi::class)

package io.ktor.server.auth

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Creates a child route protected by [scheme].
 *
 * The provider is registered in [Authentication] when this route is created. Inside [build], use
 * `principal` to access the authenticated caller as [P] without casting.
 * The first use of a provider registers it lazily; later uses of the same provider object reuse that
 * registration. A different provider with the same name is rejected.
 *
 * ```kotlin
 * val userAuth = basic<User>("user-auth") {
 *     validate { credentials -> findUser(credentials.name, credentials.password) }
 * }
 *
 * routing {
 *     authenticateWith(userAuth) {
 *         get("/me") {
 *             call.respondText(call.principal.name)
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWith)
 *
 * @param scheme typed authentication scheme used for this route.
 * @param onUnauthorized optional route-level failure handler. When `null`, the scheme-level handler or provider
 * challenge is used.
 * @param build route builder with [C] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <P, C, S> Route.authenticateWith(
    scheme: S,
    onUnauthorized: UnauthorizedHandler? = null,
    build: context(PrincipalContext<P>, C) Route.() -> Unit,
): Route where P : Any,
               S : AuthenticationScheme<P, C> {
    val route = installTypedAuthentication(scheme, isOptional = false, onUnauthorized)
    scheme.provideContext { route.build() }
    return route
}

/**
 * Creates a child route where authentication is optional.
 *
 * Requests without credentials enter the route and expose `null` from `principalOrNull`.
 * Requests with invalid credentials still invoke [onUnauthorized] or the scheme-level failure handler.
 *
 * ```kotlin
 * authenticateWithOptional(userAuth) {
 *     get("/feed") {
 *         val user = call.principalOrNull
 *         call.respondText(user?.name ?: "guest")
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWithOptional)
 *
 * @param scheme typed authentication scheme used for this route.
 * @param onUnauthorized optional route-level failure handler invoked when credentials are present but invalid.
 * @param build route builder with [C] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <P, C, S> Route.authenticateWithOptional(
    scheme: S,
    onUnauthorized: UnauthorizedHandler? = null,
    build: context(OptionalPrincipalContext<P>, C) Route.() -> Unit,
): Route where P : Any,
               S : AuthenticationScheme<P, C> {
    val route = installTypedAuthentication(scheme, isOptional = true, onUnauthorized)
    scheme.provideOptionalContext { route.build() }
    return route
}

internal fun <P : Any, C> Route.installTypedAuthentication(
    scheme: AuthenticationScheme<P, C>,
    isOptional: Boolean,
    onUnauthorized: UnauthorizedHandler? = null,
    onAccepted: (suspend RoutingContext.(P) -> Unit)? = null,
): Route {
    val selector = AuthenticationRouteSelector(listOf(scheme.name))
    val route = createChild(selector).also { scheme.preinstallAt(route = it) }
    route.install(scheme.createPlugin(isOptional, onUnauthorized, onAccepted))
    return route
}

internal fun <P : Any> typedPrincipalKey(names: List<String>, type: TypeInfo): AttributeKey<P> =
    AttributeKey("TypesafeAuth:${names.joinToString(",")}:Principal", type)

/**
 * Handles authentication failure for routes protected by [authenticateWithAnyOf].
 *
 * The handler receives the current [RoutingContext]. The map contains one [AuthenticationFailedCause] for each scheme
 * name that failed to authenticate the call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.MultiUnauthorizedHandler)
 */
public fun interface MultiUnauthorizedHandler {
    public suspend fun RoutingContext.onUnauthorized(failures: Map<String, AuthenticationFailedCause>)
}

/**
 * Creates a child route that accepts any of the provided typed authentication [schemes].
 *
 * The first scheme that authenticates the call supplies `principal` inside [build].
 * All schemes must produce principals assignable to [P].
 * Session schemes may be included, but the route context is always [PrincipalContext]. Only
 * `principal` is available inside [build]; scheme-specific context extensions such as `session` are not
 * exposed.
 * Each provider is registered lazily on first use and reused on later uses. A different provider with an
 * already registered name is rejected.
 *
 * ```kotlin
 * authenticateWithAnyOf(apiKeyAuth, bearerAuth) {
 *     get("/api/me") {
 *         call.respondText(call.principal.id)
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWithAnyOf)
 *
 * @param schemes typed schemes accepted by this route.
 * @param onUnauthorized optional handler invoked when all schemes fail. When omitted, the first scheme-level
 * [AuthenticationScheme.onUnauthorized] handler is used before default challenges are executed.
 * @param build route builder with [PrincipalContext] available as a context parameter.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> Route.authenticateWithAnyOf(
    vararg schemes: AuthenticationScheme<out P, *>,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    noinline build: context(PrincipalContext<P>) Route.() -> Unit,
): Route {
    return authenticateWithAnyOf(schemes.toList(), principalType = TypeInfo(P::class), onUnauthorized, build)
}

@PublishedApi
internal fun <P : Any> Route.authenticateWithAnyOf(
    schemes: List<AuthenticationScheme<out P, *>>,
    principalType: TypeInfo,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    build: context(PrincipalContext<P>) Route.() -> Unit,
): Route {
    require(schemes.isNotEmpty()) {
        "At least one scheme must be specified"
    }
    val names = schemes.map { it.name }
    val route = createChild(selector = AuthenticationRouteSelector(names))
    val principalKey = typedPrincipalKey<P>(names, principalType)

    for (scheme in schemes) {
        scheme.preinstallAt(route = route)
    }
    route.install(plugin = createMultiPlugin(schemes, principalKey, onUnauthorized))

    context(PrincipalContext(principalKey)) { route.build() }
    return route
}

/**
 * Creates a child route protected by [scheme] and the required [roles].
 *
 * Authentication failures are handled as in [authenticateWith]. If authentication succeeds but the resolved roles do
 * not include every required role, the forbidden handler is invoked. Route-level [onForbidden] takes precedence over
 * [AuthenticationSchemeWithRoles.forbiddenHandler] on [scheme].
 *
 * ```kotlin
 * val adminAuth = userAuth.withRoles { user ->
 *     redis.getUserRoles(user.id) // suspend lookup from Redis or database
 * }
 *
 * authenticateWith(adminAuth, roles = setOf(Role.Admin)) {
 *     get("/admin") {
 *         val user = call.principal
 *         call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWith)
 *
 * @param scheme role-based typed authentication scheme.
 * @param roles roles required to enter this route when the request is authenticated, or `null` to skip role
 * enforcement while still resolving roles for authenticated callers.
 * @param onUnauthorized optional route-level handler invoked when authentication fails.
 * @param onForbidden optional route-level handler invoked when the principal lacks required roles.
 * @param build route builder with [RolesContext] and the base scheme context available as context parameters.
 */
@ExperimentalKtorApi
public fun <P, R, C, S> Route.authenticateWith(
    scheme: AuthenticationSchemeWithRoles<P, R, C, S>,
    roles: Set<R>? = null,
    onUnauthorized: UnauthorizedHandler? = null,
    onForbidden: ForbiddenHandler<P, C, R>? = null,
    build: context(PrincipalContext<P>, C, RolesContext<P, R>) Route.() -> Unit,
): Route where P : Any,
               R : AuthenticationRole,
               S : AuthenticationScheme<P, C> {
    val route = installTypedAuthentication(
        scheme = scheme.base,
        isOptional = false,
        onUnauthorized = onUnauthorized,
        onAccepted = { principal -> scheme.validateRoles(principal, roles, onForbidden) },
    )
    scheme.base.provideContext {
        context(scheme.rolesContext) { route.build() }
    }
    return route
}

/**
 * Creates a child route where role-based authentication is optional.
 *
 * Requests without credentials enter the route and expose `null` from `principalOrNull`.
 * Requests with invalid credentials invoke [onUnauthorized] or the scheme-level failure handler.
 * When a caller is authenticated, required [roles] are enforced: missing roles invoke [onForbidden] or the
 * scheme-level forbidden handler. Requests without credentials skip role checks and enter the route with
 * `principalOrNull == null`. Use required [authenticateWith] when every caller must authenticate and satisfy [roles].
 *
 * ```kotlin
 * authenticateWithOptional(adminAuth, roles = setOf(Role.Admin)) {
 *     get("/admin") {
 *         val user = call.principalOrNull
 *         if (user == null) {
 *             call.respondText("anonymous")
 *         } else {
 *             call.respondText("${user.name}:${user.roles.joinToString(",") { it.name }}")
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWithOptional)
 *
 * @param scheme role-based typed authentication scheme.
 * @param roles roles required when the request is authenticated, or `null` to skip role enforcement while still
 * resolving roles for authenticated callers.
 * @param onUnauthorized optional route-level handler invoked when credentials are present but invalid.
 * @param onForbidden optional route-level handler invoked when the principal lacks required roles.
 * @param build route builder with the base scheme context and [RolesContext] available as context parameters.
 */
@ExperimentalKtorApi
public fun <P, R, C> Route.authenticateWithOptional(
    scheme: AuthenticationSchemeWithRoles<P, R, C, *>,
    roles: Set<R>? = null,
    onUnauthorized: UnauthorizedHandler? = null,
    onForbidden: ForbiddenHandler<P, C, R>? = null,
    build: context(OptionalPrincipalContext<P>, C, RolesContext<P, R>) Route.() -> Unit,
): Route where P : Any,
               R : AuthenticationRole {
    val route = installTypedAuthentication(
        scheme = scheme.base,
        isOptional = true,
        onUnauthorized = onUnauthorized,
        onAccepted = { principal -> scheme.validateRoles(principal, roles, onForbidden) },
    )
    scheme.base.provideOptionalContext {
        context(scheme.rolesContext) { route.build() }
    }
    return route
}
