/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Handles an authentication failure for a typed authentication scheme.
 *
 * The handler receives the current [RoutingContext] and the [AuthenticationFailedCause] for the failed authentication
 * attempt.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.UnauthorizedHandler)
 */
public fun interface UnauthorizedHandler {
    public suspend fun RoutingContext.onUnauthorized(cause: AuthenticationFailedCause)
}

private val RegisteredProvidersKey = AttributeKey<MutableMap<String, AuthenticationProvider>>(
    "TypesafeAuthRegisteredProviders"
)

internal fun interface PrincipalResolver<P : Any> {
    suspend fun resolveFrom(ctx: AuthenticationContext): P?
}

internal interface AuthenticationSchemeExtension<C> {
    val context: C

    fun preinstallAt(route: Route) {}
}

@PublishedApi
internal object EmptyAuthenticationSchemeExtension : AuthenticationSchemeExtension<Unit> {
    override val context: Unit = Unit
}

internal object MapPrincipalFailureKey

/**
 * The provider is registered lazily when the scheme is first used by [authenticateWith]. Reusing the same provider
 * object, including through [mapPrincipal] or [orAnonymous], reuses the existing application registration. Creating a
 * different provider with the same name fails fast because provider names are application-wide identifiers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticationScheme)
 *
 * @param P the principal type produced by this scheme.
 * @param C the intrinsic extension context supplied to authenticated routes.
 * @property name name that identifies this authentication scheme.
 * @property onUnauthorized default failure handler for routes that use the scheme. A route-level handler passed to
 * [authenticateWith] overrides this value.
 */
@ExperimentalKtorApi
public class AuthenticationScheme<P, C> @PublishedApi internal constructor(
    @PublishedApi
    internal val provider: AuthenticationProvider,
    internal val principalType: KClass<P>,
    public val onUnauthorized: UnauthorizedHandler?,
    @PublishedApi
    internal val extension: AuthenticationSchemeExtension<C>,
    @PublishedApi
    internal val principalResolver: PrincipalResolver<P> = { ctx ->
        ctx.principal(provider.name, klass = principalType)
    },
) where P : Any {
    public val name: String = checkNotNull(provider.name) {
        "Typed authentication schemes require a named AuthenticationProvider"
    }

    internal val principalKey = AttributeKey<P>("TypesafeAuth:$name:Principal", TypeInfo(principalType))

    private fun Application.registerProviderIfNeeded() {
        val registered = attributes.computeIfAbsent(RegisteredProvidersKey) { mutableMapOf() }
        val existing = registered[name]
        if (existing != null) {
            check(existing === provider) {
                "Authentication provider name `$name` is already used by a different provider " +
                    "${existing::class.simpleName}. Use a unique scheme name."
            }
            return
        }
        registered[name] = provider
        authentication { register(provider) }
    }

    internal fun preinstallAt(route: Route) {
        extension.preinstallAt(route)
        route.application.registerProviderIfNeeded()
    }

    internal val requiredContext: PrincipalContext<P> = PrincipalContext(principalKey)

    internal val optionalContext: OptionalPrincipalContext<P> = OptionalPrincipalContext(principalKey)

    internal inline fun provideContext(
        block: context(PrincipalContext<P>, C) () -> Unit
    ) = context(requiredContext, extension.context) { block() }

    internal inline fun provideOptionalContext(
        block: context(OptionalPrincipalContext<P>, C) () -> Unit
    ) = context(optionalContext, extension.context) { block() }

    public companion object {
        /**
         * Creates a [AuthenticationScheme] with principal type [P] and no additional context.
         *
         * @param provider named authentication provider used by the scheme.
         * @param onUnauthorized default failure handler, or `null` to use the provider challenge.
         * @return a simple typed authentication scheme with principal type [P].
         */
        @InternalAPI
        public inline fun <reified P : Any> from(
            provider: AuthenticationProvider,
            onUnauthorized: UnauthorizedHandler?
        ): SimpleAuthenticationScheme<P> = AuthenticationScheme(
            provider = provider,
            principalType = P::class,
            onUnauthorized = onUnauthorized,
            extension = EmptyAuthenticationSchemeExtension,
        )
    }
}

/**
 * Returns a scheme that accepts anonymous requests when no credentials are provided.
 *
 * Requests without credentials receive the principal produced by [fallback].
 * Requests with invalid credentials still fail authentication.
 * Use the returned scheme with [authenticateWith] and read `principal` inside the route block.
 *
 * Combining this scheme with [authenticateWithOptional] is allowed but redundant: requests without credentials
 * already receive the anonymous principal, so `principalOrNull` is not `null` in that case.
 *
 * ```kotlin
 * interface Identity
 * data class AuthenticatedUser(val id: String) : Identity
 * data class GuestUser(val label: String = "guest") : Identity
 *
 * val auth = basic<AuthenticatedUser>("users") {
 *     validate { credentials -> findUser(credentials) }
 * }.orAnonymous { GuestUser() }
 *
 * routing {
 *     authenticateWith(auth) {
 *         get("/feed") {
 *             when (val user = call.principal) {
 *                 is AuthenticatedUser -> call.respondText("auth:${user.id}")
 *                 is GuestUser -> call.respondText("guest:${user.label}")
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.orAnonymous)
 *
 * @param P authenticated principal type produced when credentials are valid.
 * @param AP anonymous principal type returned by [fallback].
 * @param CP common principal type shared by authenticated and anonymous principals.
 * @param fallback creates the anonymous principal when no credentials are present.
 * @return a scheme that produces [CP] for authenticated and anonymous callers.
 */
@ExperimentalKtorApi
public inline fun <reified CP, P, AP> SimpleAuthenticationScheme<P>.orAnonymous(
    noinline fallback: suspend RoutingContext.() -> AP,
): SimpleAuthenticationScheme<CP> where CP : Any, P : AP, AP : CP =
    orAnonymous(fallback, commonPrincipalType = CP::class)

@PublishedApi
internal fun <P, AP, CP> SimpleAuthenticationScheme<P>.orAnonymous(
    fallback: suspend RoutingContext.() -> AP,
    commonPrincipalType: KClass<CP>,
): SimpleAuthenticationScheme<CP> where CP : Any, P : AP, AP : CP =
    AuthenticationScheme(
        provider = provider,
        principalType = commonPrincipalType,
        onUnauthorized = onUnauthorized,
        principalResolver = { ctx ->
            val principal = principalResolver.resolveFrom(ctx)
            when {
                principal != null -> principal
                ctx.failedWithNoCredentials() -> fallback(ctx.call.toRoutingContext())
                else -> null
            }
        },
        extension = EmptyAuthenticationSchemeExtension,
    )

/**
 * Returns a scheme that produces [R] by transforming every successfully resolved principal of type [P].
 *
 * The returned scheme reuses this scheme's provider and extension. If the original resolver returns `null`,
 * the transformation is not invoked and authentication fails as before. If [transform] returns `null`, the scheme
 * registers an [AuthenticationFailedCause.InvalidCredentials] challenge and rejects both required and optional
 * authentication. Route-level and scheme-level [onUnauthorized] handlers receive that cause. When neither handler
 * nor a provider challenge responds, authentication fails with `401 Unauthorized`. Exceptions thrown by [transform]
 * propagate.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.mapPrincipal)
 *
 * @param P the principal type produced by this scheme.
 * @param R the principal type produced by [transform].
 * @param C the intrinsic extension context preserved on the returned scheme.
 * @param transform maps a successfully resolved principal to [R], or `null` to reject the credentials.
 * @return a scheme that produces [R] and reuses this scheme's provider and extension.
 */
@ExperimentalKtorApi
public inline fun <P, reified R, C> AuthenticationScheme<P, C>.mapPrincipal(
    noinline transform: suspend RoutingContext.(P) -> R?,
): AuthenticationScheme<R, C> where P : Any, R : Any =
    mapPrincipal(transform, resultType = R::class)

@PublishedApi
internal fun <P : Any, R : Any, C> AuthenticationScheme<P, C>.mapPrincipal(
    transform: suspend RoutingContext.(P) -> R?,
    resultType: KClass<R>
): AuthenticationScheme<R, C> =
    AuthenticationScheme(
        provider = provider,
        principalType = resultType,
        onUnauthorized = onUnauthorized,
        extension = extension,
        principalResolver = { ctx ->
            principalResolver.resolveFrom(ctx)?.let { source ->
                val mapped = transform(ctx.call.toRoutingContext(), source)
                if (mapped == null) {
                    ctx.challenge(
                        MapPrincipalFailureKey,
                        AuthenticationFailedCause.InvalidCredentials
                    ) { challenge, _ ->
                        challenge.complete()
                    }
                }
                mapped
            }
        },
    )

/**
 * Typed authentication scheme with no additional context.
 *
 * Most built-in providers such as [basic], [bearer], and [jwt] return this scheme type.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SimpleAuthenticationScheme)
 */
public typealias SimpleAuthenticationScheme<P> = AuthenticationScheme<P, Unit>

internal fun AuthenticationContext.lastFailureOrNoCredentials(): AuthenticationFailedCause =
    challenge.register.lastOrNull()?.first ?: allFailures.lastOrNull() ?: AuthenticationFailedCause.NoCredentials

internal fun AuthenticationContext.failedWithNoCredentials(): Boolean =
    lastFailureOrNoCredentials() is AuthenticationFailedCause.NoCredentials
