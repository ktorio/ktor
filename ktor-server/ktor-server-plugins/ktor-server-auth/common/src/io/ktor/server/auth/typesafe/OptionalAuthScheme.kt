/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Converts this scheme into an optional scheme.
 *
 * Requests without credentials continue through the route with `principal == null`. Requests with invalid credentials
 * are still rejected by the original authentication scheme.
 *
 * ```kotlin
 * val optionalAuth = userAuth.optional()
 *
 * routing {
 *     authenticateWith(optionalAuth) {
 *         get("/me") {
 *             call.respondText(principal?.name ?: "anonymous")
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.optional)
 *
 * @return a typed scheme that exposes a nullable principal.
 */
@ExperimentalKtorApi
public fun <P : Any> DefaultAuthScheme<P, *>.optional(): OptionalAuthScheme<P> = OptionalAuthScheme(base = this)

/**
 * Converts this scheme into an optional scheme with an anonymous fallback.
 *
 * Requests without credentials continue through the route with a principal created by [fallback]. Requests with
 * invalid credentials are still rejected by the original authentication scheme. The route principal type is the common
 * supertype [B] of authenticated principals [P] and anonymous principals [AP].
 *
 * ```kotlin
 * val publicAuth: AnonymousAuthScheme<Identity, User, Guest> = userAuth.optional { Guest() }
 *
 * routing {
 *     authenticateWith(publicAuth) {
 *         get("/me") {
 *             call.respondText(principal.displayName)
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.optional)
 *
 * @param fallback creates a fallback principal when credentials are missing.
 * @return a typed scheme that exposes an authenticated or anonymous principal.
 */
@ExperimentalKtorApi
public fun <B : Any, P : B, AP : B> DefaultAuthScheme<P, *>.optional(
    fallback: suspend (ApplicationCall) -> AP
): AnonymousAuthScheme<B, P, AP> {
    return AnonymousAuthScheme(base = this, anonymousFactory = fallback)
}

/**
 * Optional typed authentication scheme where missing credentials produce a nullable principal.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.OptionalAuthScheme)
 *
 * @param P the principal type produced when authentication succeeds.
 * @property base scheme used to authenticate requests that include credentials.
 */
@ExperimentalKtorApi
public class OptionalAuthScheme<P : Any>(
    public val base: DefaultAuthScheme<P, *>
) : AuthScheme<P, OptionalAuthenticatedContext<P>> {
    override val name: String = base.name

    override fun createAuthenticatedContext(route: Route): OptionalAuthenticatedContext<P> =
        OptionalAuthenticatedContext(base.principalKey)

    internal fun install(route: Route): OptionalAuthenticatedContext<P> {
        base.install(
            route = route,
            kind = "Optional",
            optional = true
        )
        return createAuthenticatedContext(route)
    }
}

/**
 * Optional typed authentication scheme where missing credentials produce an anonymous principal.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.AnonymousAuthScheme)
 *
 * @param B the common supertype of authenticated and anonymous principals.
 * @param P the authenticated principal type.
 * @param AP the anonymous principal type.
 * @property base scheme used to authenticate requests that include credentials.
 */
@ExperimentalKtorApi
public class AnonymousAuthScheme<B : Any, P : B, AP : B>(
    public val base: DefaultAuthScheme<P, *>,
    internal val anonymousFactory: suspend (ApplicationCall) -> AP
) : AuthScheme<P, DefaultAuthenticatedContext<B>> {
    override val name: String = base.name
    private val principalKey: AttributeKey<B> =
        AttributeKey("TypesafeAuth:$name:AnonymousPrincipal", TypeInfo(Any::class))

    override fun createAuthenticatedContext(route: Route): DefaultAuthenticatedContext<B> =
        DefaultAuthenticatedContext(principalKey)

    internal fun install(route: Route): DefaultAuthenticatedContext<B> {
        base.install(
            route = route,
            kind = "Anonymous",
            optional = true,
            onAccepted = { call ->
                val ctx = call.authentication
                val principal: B = base.principalFrom(ctx) ?: anonymousFactory(call).also { anonymous ->
                    // Use ctx.principal directly; anonymous type AP may differ from base type P.
                    ctx.principal(provider = base.name, principal = anonymous)
                }
                call.attributes.put(principalKey, principal)
            }
        )
        return createAuthenticatedContext(route)
    }
}
