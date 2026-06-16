/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.auth.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Resolves a route principal from a stored session value.
 *
 * The resolver receives the current [RoutingContext] and the session value.
 *
 * Return `null` to reject the session.
 */
public typealias SessionPrincipalResolver<S, P> = suspend RoutingContext.(S) -> P?

/**
 * Transforms a session value before principal resolution.
 *
 * Return the session value that should be validated for the current call, or `null` to reject the session.
 */
public typealias SessionTransformer<S> = suspend RoutingContext.(S) -> S?

/**
 * Configures the [Sessions] plugin for a typed session authentication scheme.
 *
 * @param S stored session type.
 * @param P route principal type.
 * @param C authenticated route context type.
 */
@KtorDsl
public typealias SessionsPluginConfig<S, P, C> = SessionsConfig.(SessionAuthScheme<S, P, C>) -> Unit

/**
 * Configures a typed Session authentication scheme.
 *
 * Unlike [SessionAuthenticationProvider.Config], [validate] returns [P] from a stored session value [S], so routes
 * protected by [authenticateWith] can read [ApplicationCall.principal] as [P] and [ApplicationCall.session] as [S].
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * Session authentication responds with its default `401 Unauthorized` challenge.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig)
 *
 * @param S the stored session type.
 * @param P the principal type exposed to authenticated routes.
 */
@KtorDsl
public open class TypedSessionAuthConfig<
    S : Any,
    P : Any,
    C : SessionAuthenticatedContext<S, P>
    > @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.description)
     */
    internal var description: String? = null

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Session
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.onUnauthorized)
     */
    internal var onUnauthorized: UnauthorizedHandler? = null

    internal var principalResolver: SessionPrincipalResolver<S, P>? = null

    internal var sessionTransformer: SessionTransformer<S>? = null

    internal var csrfConfig: (CSRFConfig.() -> Unit)? = null

    internal var sessionsPluginConfig: SessionsPluginConfig<S, P, *>? = null

    /**
     * Creates the authenticated route context from the default session context.
     *
     * This internal API is intended for integrations that need provider-bound helpers in typed route bodies.
     */
    @InternalAPI
    public var contextFactory: ((SessionContext<S, P>) -> C)? = null

    /**
     * Sets a validation function for the session value.
     *
     * Return the principal of type [P] when the session is accepted, or `null` when the session is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and session value read by the
     * [io.ktor.server.sessions.Sessions] plugin.
     */
    public fun validate(body: suspend RoutingContext.(S) -> P?) {
        principalResolver = body
    }

    /**
     * Transforms the session value before [validate] resolves the route principal.
     *
     * This hook is intended for integrations that need to update or invalidate a stored session as part of
     * authentication. Return the effective session value for this request, or `null` to reject the session.
     *
     * The stored session is rewritten only when the returned value is different instance as the incoming
     * session (`!=`). Returning the same object skips [io.ktor.server.sessions.CurrentSession.set].
     *
     * @param block transformation function called with the session value read by the
     * [io.ktor.server.sessions.Sessions] plugin.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.transformSession)
     */
    public fun transformSession(block: SessionTransformer<S>) {
        sessionTransformer = block
    }

    /**
     * Configures CSRF protection for routes authenticated with this session scheme.
     *
     * @param config CSRF plugin configuration.
     */
    public fun csrfProtection(config: CSRFConfig.() -> Unit) {
        csrfConfig = config
    }

    /**
     * Configures how the typed session scheme installs the [io.ktor.server.sessions.Sessions] plugin.
     *
     * @param config session plugin configuration block.
     */
    public fun transport(config: SessionsPluginConfig<S, P, *>) {
        sessionsPluginConfig = config
    }

    @PublishedApi
    internal fun buildProvider(
        name: String,
        sessionType: KClass<S>,
        sessionKey: AttributeKey<S>
    ): SessionAuthenticationProvider<S> {
        val config = SessionAuthenticationProvider.Config(name, description, sessionType)
        val resolver = requireNotNull(principalResolver) { "Principal resolver cannot be null" }
        val transformer = sessionTransformer
        config.validate { session ->
            val routingContext = toRoutingContext()
            val effectiveSession = if (transformer != null) {
                val updatedSession = routingContext.transformer(session) ?: return@validate null
                if (updatedSession != session) {
                    sessions.set(name, updatedSession)
                }
                updatedSession
            } else {
                session
            }
            val principal = routingContext.resolver(effectiveSession)
            if (principal != null) {
                attributes.put(sessionKey, effectiveSession)
            }
            principal
        }
        return config.buildProvider()
    }
}
