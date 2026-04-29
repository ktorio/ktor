/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Builds a route with an authenticated context receiver.
 *
 * Typed route builders use this function type so route handlers can access [ApplicationCall.principal],
 * [ApplicationCall.roles], or custom context extensions without calling [io.ktor.server.auth.principal].
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
 * The scheme is registered in [Authentication] when this route is created. Inside [build], use
 * [ApplicationCall.principal] to access the principal as [P] without casting.
 * The first use of a scheme instance registers it lazily; later uses of the same scheme instance reuse that
 * registration. A different scheme instance with the same name is rejected.
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme typed authentication scheme used for this route.
 * @param onUnauthorized optional route-level failure handler. When `null`, the scheme-level handler or provider
 * challenge is used.
 * @param build route builder with [C] available as a context parameter.
 */
public fun <P : Any, C : AuthenticatedContext<P>> Route.authenticateWith(
    scheme: DefaultAuthScheme<P, C>,
    onUnauthorized: UnauthorizedHandler? = null,
    build: AuthenticatedRouteBuilder<C>
): Route {
    val selector = AuthenticationRouteSelector(listOf(scheme.name))
    val route = createChild(selector)
    with(scheme.install(route, onUnauthorized)) {
        route.build()
    }
    return route
}

/**
 * Handles authentication failure for routes protected by [authenticateWithAnyOf].
 *
 * The handler receives the current [RoutingContext]. The map contains one [AuthenticationFailedCause] for each scheme
 * name that failed to authenticate the call.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.MultiUnauthorizedHandler)
 */
public typealias MultiUnauthorizedHandler = suspend RoutingContext.(Map<String, AuthenticationFailedCause>) -> Unit

/**
 * Creates a child route that accepts any of the provided typed authentication [schemes].
 *
 * The first scheme that authenticates the call supplies the [ApplicationCall.principal] available inside [build].
 * All schemes must produce principals assignable to [P].
 * Each scheme instance is registered lazily on first use and reused on later uses. A different scheme instance with an
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWithAnyOf)
 *
 * @param schemes typed schemes accepted by this route.
 * @param onUnauthorized optional handler invoked when all schemes fail.
 * @param build route builder with [PrincipalContext] available as a context parameter.
 */
public inline fun <reified P : Any> Route.authenticateWithAnyOf(
    vararg schemes: DefaultAuthScheme<out P, *>,
    noinline onUnauthorized: MultiUnauthorizedHandler? = null,
    noinline build: AuthenticatedRouteBuilder<PrincipalContext<P>>
): Route {
    return authenticateWithAnyOf(schemes.toList(), principalType = TypeInfo(P::class), onUnauthorized, build)
}

@PublishedApi
internal fun <P : Any> Route.authenticateWithAnyOf(
    schemes: List<DefaultAuthScheme<out P, *>>,
    principalType: TypeInfo,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    build: AuthenticatedRouteBuilder<PrincipalContext<P>>
): Route {
    require(schemes.isNotEmpty()) {
        "At least one scheme must be specified"
    }
    val names = schemes.map { it.name }
    val route = createChild(selector = AuthenticationRouteSelector(names))
    val principalKeyName = "TypesafeAuth:${names.joinToString(",")}:Principal"
    val principalKey = AttributeKey<P>(principalKeyName, principalType)
    with(route.installTypedMultiAuthInterceptor(schemes, principalKey, onUnauthorized)) {
        route.build()
    }
    return route
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
 *         call.respondText(call.principal.name)
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
public fun <P : Any, R : AuthRole> Route.authenticateWith(
    scheme: RoleBasedAuthScheme<P, R>,
    roles: Set<R>,
    onUnauthorized: UnauthorizedHandler? = null,
    onForbidden: ForbiddenHandler<R>? = null,
    build: AuthenticatedRouteBuilder<RoleBasedContext<P, R>>
): Route {
    val route = createChild(selector = AuthenticationRouteSelector(names = listOf(scheme.base.name)))
    val context = scheme.install(route, roles, onUnauthorized, onForbidden)
    with(context) {
        route.build()
    }
    return route
}

/**
 * Creates a child route where authentication is optional and [ApplicationCall.principal] is nullable.
 *
 * Requests without credentials continue with `call.principal == null`. Requests with invalid credentials are rejected
 * by the original authentication scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme optional typed authentication scheme.
 * @param build route builder with [OptionalPrincipalContext] available as a context parameter.
 */
public fun <P : Any> Route.authenticateWith(
    scheme: OptionalAuthScheme<P>,
    build: AuthenticatedRouteBuilder<OptionalPrincipalContext<P>>
): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    with(scheme.install(route)) {
        route.build()
    }
    return route
}

/**
 * Creates a child route where missing credentials are replaced by an anonymous principal.
 *
 * Requests without credentials continue with the fallback principal configured by [orAnonymous]. Requests with invalid
 * credentials are rejected by the original authentication scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme anonymous typed authentication scheme with a fallback factory.
 * @param build route builder with [PrincipalContext] of the common supertype.
 */
public fun <B : Any, P : B, AP : B> Route.authenticateWith(
    scheme: AnonymousAuthScheme<B, P, AP>,
    build: AuthenticatedRouteBuilder<PrincipalContext<B>>
): Route {
    val route = createChild(AuthenticationRouteSelector(listOf(scheme.name)))
    with(scheme.install(route)) {
        route.build()
    }
    return route
}

/**
 * Creates a child route protected by the session part of an [OAuthWithSessionScheme].
 *
 * Use [oauthCallback] to create the OAuth callback route that stores the session, then use this route builder to
 * protect routes that require that session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.authenticateWith)
 *
 * @param scheme compound OAuth and Session authentication scheme.
 * @param onUnauthorized optional route-level handler invoked when session authentication fails.
 * @param build route builder with [SessionAuthenticatedContext] available as a context parameter.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> Route.authenticateWith(
    scheme: OAuthWithSessionScheme<S, P>,
    onUnauthorized: UnauthorizedHandler? = null,
    build: AuthenticatedRouteBuilder<SessionAuthenticatedContext<S, P>>
): Route {
    return authenticateWith(scheme.sessionScheme, onUnauthorized, build)
}
