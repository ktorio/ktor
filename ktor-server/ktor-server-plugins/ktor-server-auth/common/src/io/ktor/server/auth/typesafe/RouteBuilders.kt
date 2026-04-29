/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Builds a route with an authenticated context receiver.
 *
 * Typed route builders use this function type so route handlers can access [principal], [roles], or custom context
 * extensions without calling `call.principal<T>()`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AuthenticatedRouteBuilder)
 *
 * @param C the context type available inside the route builder.
 */
public typealias AuthenticatedRouteBuilder<C> = context(C)
Route.() -> Unit

/**
 * Creates a child route protected by [scheme].
 *
 * The scheme is registered in [Authentication] when this route is created. Inside [build], use [principal] to access
 * the principal as [P] without casting.
 *
 * ```kotlin
 * val userAuth = basic<User>("user-auth") {
 *     validate { credentials -> findUser(credentials.name, credentials.password) }
 * }
 *
 * routing {
 *     authenticateWith(userAuth) {
 *         get("/me") {
 *             call.respondText(principal.name)
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme typed authentication scheme used for this route.
 * @param onUnauthorized optional route-level failure handler. When `null`, the scheme-level handler or provider
 * challenge is used.
 * @param build route builder with [C] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <P : Any, C : AuthenticatedContext<P>> Route.authenticateWith(
    scheme: DefaultAuthScheme<P, C>,
    onUnauthorized: UnauthorizedHandler? = null,
    build: AuthenticatedRouteBuilder<C>
): Route {
    application.registerSchemeIfNeeded(scheme)
    val selector = AuthenticationRouteSelector(listOf(scheme.name))
    val route = createChild(selector)
    scheme.install(route, onUnauthorized)
    with(scheme.createAuthenticatedContext(route)) {
        route.build()
    }
    return route
}

/**
 * Handles authentication failure for routes protected by [authenticateWithAnyOf].
 *
 * The map contains one [AuthenticationFailedCause] for each scheme name that failed to authenticate the call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.MultiUnauthorizedHandler)
 */
public typealias MultiUnauthorizedHandler = suspend (ApplicationCall, Map<String, AuthenticationFailedCause>) -> Unit

/**
 * Creates a child route that accepts any of the provided typed authentication [schemes].
 *
 * The first scheme that authenticates the call supplies the [principal] available inside [build]. All schemes must
 * produce principals assignable to [P].
 *
 * ```kotlin
 * authenticateWithAnyOf(apiKeyAuth, bearerAuth) {
 *     get("/api/me") {
 *         call.respondText(principal.id)
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWithAnyOf)
 *
 * @param schemes typed schemes accepted by this route.
 * @param onUnauthorized optional handler invoked when all schemes fail.
 * @param build route builder with [DefaultAuthenticatedContext] available as a context parameter.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> Route.authenticateWithAnyOf(
    vararg schemes: DefaultAuthScheme<out P, *>,
    noinline onUnauthorized: MultiUnauthorizedHandler? = null,
    noinline build: AuthenticatedRouteBuilder<DefaultAuthenticatedContext<P>>
): Route {
    return authenticateWithAnyOf(schemes.toList(), P::class, onUnauthorized, build)
}

@OptIn(ExperimentalKtorApi::class)
@PublishedApi
internal fun <P : Any> Route.authenticateWithAnyOf(
    schemes: List<DefaultAuthScheme<out P, *>>,
    klass: KClass<P>,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    build: AuthenticatedRouteBuilder<DefaultAuthenticatedContext<P>>
): Route {
    require(schemes.isNotEmpty()) {
        "At least one scheme must be specified"
    }
    for (scheme in schemes) application.registerSchemeIfNeeded(scheme)
    val names = schemes.map { it.name }
    val route = createChild(selector = AuthenticationRouteSelector(names))
    val principalKeyName = "TypesafeAuth:${names.joinToString(",")}:Principal"
    val principalKey = AttributeKey<P>(principalKeyName, TypeInfo(klass))
    route.install(createTypedMultiAuthInterceptor(schemes, principalKey, onUnauthorized, route))
    with(DefaultAuthenticatedContext(principalKey)) {
        route.build()
    }
    return route
}

/**
 * Handles authorization failure for a role-protected typed route.
 *
 * The handler receives the current [ApplicationCall] and the roles required by the route.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.ForbiddenHandler)
 */
@OptIn(ExperimentalKtorApi::class)
public typealias ForbiddenHandler = suspend (ApplicationCall, Set<AuthRole>) -> Unit

/**
 * Creates a child route protected by [scheme] and the required [roles].
 *
 * Authentication failures are handled as in [authenticateWith]. If authentication succeeds but the resolved roles do
 * not include every required role, [onForbidden] or the scheme-level forbidden handler is invoked.
 *
 * ```kotlin
 * val adminAuth = userAuth.withRoles { user -> user.roles }
 *
 * authenticateWith(adminAuth, roles = setOf(Role.Admin)) {
 *     get("/admin") {
 *         call.respondText(principal.name)
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme role-based typed authentication scheme.
 * @param roles roles required to enter this route.
 * @param onUnauthorized optional route-level handler invoked when authentication fails.
 * @param onForbidden optional route-level handler invoked when the principal lacks required roles.
 * @param build route builder with [RoleBasedContext] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <P : Any, R : AuthRole> Route.authenticateWith(
    scheme: RoleBasedAuthScheme<P, R, *>,
    roles: Set<R>,
    onUnauthorized: UnauthorizedHandler? = null,
    onForbidden: ForbiddenHandler? = null,
    build: AuthenticatedRouteBuilder<RoleBasedContext<P, R>>
): Route {
    application.registerSchemeIfNeeded(scheme.base)
    val route = createChild(selector = AuthenticationRouteSelector(names = listOf(scheme.base.name)))

    route.install(
        scheme.base.createTypedAuthPlugin(
            route = route,
            kind = "Roles",
            onUnauthorized = onUnauthorized ?: scheme.base.onUnauthorized,
            onAccepted = { call -> scheme.validateRoles(call, roles, onForbidden) }
        )
    )
    with(scheme.createAuthenticatedContext(route)) {
        route.build()
    }
    return route
}

/**
 * Creates a child route where authentication is optional and [principal] is nullable.
 *
 * Requests without credentials continue with `principal == null`. Requests with invalid credentials are rejected by
 * the original authentication scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme optional typed authentication scheme.
 * @param build route builder with [OptionalAuthenticatedContext] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <P : Any> Route.authenticateWith(
    scheme: OptionalAuthScheme<P>,
    build: AuthenticatedRouteBuilder<OptionalAuthenticatedContext<P>>
): Route {
    application.registerSchemeIfNeeded(scheme.base)
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    scheme.installAuthInterceptor(route)
    with(scheme.createAuthenticatedContext(route)) {
        route.build()
    }
    return route
}

/**
 * Creates a child route where missing credentials are replaced by an anonymous principal.
 *
 * Requests without credentials continue with the fallback principal configured by [optional]. Requests with invalid
 * credentials are rejected by the original authentication scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme anonymous typed authentication scheme with a fallback factory.
 * @param build route builder with [DefaultAuthenticatedContext] of the common supertype.
 */
@ExperimentalKtorApi
public fun <B : Any, P : B, AP : B> Route.authenticateWith(
    scheme: AnonymousAuthScheme<B, P, AP>,
    build: AuthenticatedRouteBuilder<DefaultAuthenticatedContext<B>>
): Route {
    application.registerSchemeIfNeeded(scheme.base)
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    scheme.install(route)
    with(scheme.createAuthenticatedContext(route)) {
        route.build()
    }
    return route
}
