/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Creates a child route protected by [scheme].
 *
 * The scheme is registered in [Authentication] when this route is created. Inside [build], use
 * Inside [build], use `principal` to access the authenticated caller as [P] without casting.
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
    build: context(C, PrincipalOptionality.Required) Route.() -> Unit,
): Route where P : Any,
               C : AuthenticatedContext<P>,
               S : AuthenticationScheme<P, C> =
    authenticateWithInternal(scheme, optionality = PrincipalOptionality.Required, onUnauthorized, build = build)

/**
 * Creates a child route where authentication is optional.
 *
 * Requests without credentials enter the route and expose `null` from `principalOrNull`.
 * Requests with invalid credentials still invoke [onUnauthorized] or the scheme-level failure handler.
 *
 * This function cannot be combined with [orAnonymous]. Use [authenticateWith] with an [orAnonymous] scheme instead.
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
    build: context(C, PrincipalOptionality.Optional) Route.() -> Unit,
): Route where P : Any,
               C : AuthenticatedContext<P>,
               S : AuthenticationScheme<P, C> =
    authenticateWithInternal(scheme, optionality = PrincipalOptionality.Optional, onUnauthorized, build = build)

@OptIn(ExperimentalKtorApi::class)
internal fun <P, O, C, S> Route.authenticateWithInternal(
    scheme: S,
    optionality: O,
    onUnauthorized: UnauthorizedHandler? = null,
    onAccepted: (suspend RoutingContext.(P) -> Unit)? = null,
    build: context(C, O) Route.() -> Unit,
): Route where P : Any,
               O : PrincipalOptionality,
               C : AuthenticatedContext<P>,
               S : AuthenticationScheme<P, C> {
    val isOptional = optionality == PrincipalOptionality.Optional
    require(!isOptional || scheme.anonymousFactory == null) {
        "authenticateWithOptional cannot be used with orAnonymous schemes. " +
            "Use authenticateWith with orAnonymous instead."
    }

    val selector = AuthenticationRouteSelector(listOf(scheme.name))
    val route = createChild(selector).also { scheme.preinstallAt(route = it) }
    val plugin = scheme.createPlugin(isOptional, onUnauthorized, onAccepted)

    route.install(plugin)
    context(scheme.context, optionality) { route.build() }
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
 * Session schemes may be included, but the route context is always [AuthenticatedContext]. Only
 * `principal` is available inside [build]; scheme-specific context extensions such as `session` are not
 * exposed.
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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticateWithAnyOf)
 *
 * @param schemes typed schemes accepted by this route.
 * @param onUnauthorized optional handler invoked when all schemes fail. When omitted, the first scheme-level
 * [AuthenticationScheme.onUnauthorized] handler is used before default challenges are executed.
 * @param build route builder with [AuthenticatedContext] available as a context parameter.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> Route.authenticateWithAnyOf(
    vararg schemes: AuthenticationScheme<out P, *>,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    noinline build: context(AuthenticatedContext<P>, PrincipalOptionality.Required) Route.() -> Unit,
): Route {
    return authenticateWithAnyOf(schemes.toList(), principalType = TypeInfo(P::class), onUnauthorized, build)
}

@PublishedApi
@OptIn(ExperimentalKtorApi::class)
internal fun <P : Any> Route.authenticateWithAnyOf(
    schemes: List<AuthenticationScheme<out P, *>>,
    principalType: TypeInfo,
    onUnauthorized: MultiUnauthorizedHandler? = null,
    build: context(AuthenticatedContext<P>, PrincipalOptionality.Required) Route.() -> Unit,
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

    context(AuthenticatedContext(principalKey), PrincipalOptionality.Required) { route.build() }
    return route
}

/**
 * Creates a child route protected by [scheme] and the required [roles].
 *
 * Authentication failures are handled as in [authenticateWith]. If authentication succeeds but the resolved roles do
 * not include every required role, the forbidden handler is invoked. Route-level [onForbidden] takes precedence over
 * [AuthenticationSchemeWithRoles.onForbidden] on [scheme].
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
    build: context(C, PrincipalOptionality.Required, RolesContext<P, R>) Route.() -> Unit,
): Route where P : Any,
               R : AuthenticationRole,
               C : AuthenticatedContext<P>,
               S : AuthenticationScheme<P, C> =
    authenticateWith(
        scheme,
        roles,
        optionality = PrincipalOptionality.Required,
        onUnauthorized,
        onForbidden,
        build
    )

/**
 * Creates a child route where role-based authentication is optional.
 *
 * Requests without credentials enter the route and expose `null` from `principalOrNull`.
 * Requests with invalid credentials invoke [onUnauthorized] or the scheme-level failure handler.
 * When a caller is authenticated, required [roles] are enforced: missing roles invoke [onForbidden] or the
 * scheme-level forbidden handler. Requests without credentials skip role checks and enter the route with
 * `principalOrNull == null`. Use required [authenticateWith] when every caller must authenticate and satisfy [roles].
 *
 * This function cannot be combined with [orAnonymous]. Use [authenticateWith] with an [orAnonymous] scheme instead.
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
    build: context(C, PrincipalOptionality.Optional, RolesContext<P, R>) Route.() -> Unit,
): Route where P : Any,
               R : AuthenticationRole,
               C : AuthenticatedContext<P> =
    authenticateWith(scheme, roles, optionality = PrincipalOptionality.Optional, onUnauthorized, onForbidden, build)

@ExperimentalKtorApi
internal fun <P, O, R, C> Route.authenticateWith(
    scheme: AuthenticationSchemeWithRoles<P, R, C, *>,
    roles: Set<R>?,
    optionality: O,
    onUnauthorized: UnauthorizedHandler? = null,
    onForbidden: ForbiddenHandler<P, C, R>? = null,
    build: context(C, O, RolesContext<P, R>) Route.() -> Unit,
): Route where P : Any,
               R : AuthenticationRole,
               O : PrincipalOptionality,
               C : AuthenticatedContext<P> =
    authenticateWithInternal(
        scheme = scheme.base,
        optionality = optionality,
        onUnauthorized = onUnauthorized,
        onAccepted = { principal -> scheme.validateRoles(principal, roles, onForbidden) },
    ) {
        context(scheme.base.context, optionality, scheme.rolesContext) { build() }
    }
