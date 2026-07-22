/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

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
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionPrincipalResolver)
 */
public typealias SessionPrincipalResolver<S, P> = suspend RoutingContext.(S) -> P?

/**
 * Transforms a session value before principal resolution.
 *
 * Return the session value that should be validated for the current call, or `null` to reject the session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionTransformer)
 */
public typealias SessionTransformer<S> = suspend RoutingContext.(S) -> S?

/**
 * Configures a typed Session authentication scheme.
 *
 * Unlike [SessionAuthenticationProvider.Config], [validate] returns [P] from a stored session value [S], so routes
 * protected by [authenticateWith] can read [io.ktor.server.application.ApplicationCall.principal] as [P] and
 * [io.ktor.server.application.ApplicationCall.session] as [S].
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
@ExperimentalKtorApi
@KtorDsl
public open class TypedSessionAuthConfig<S : Any, P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Session
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    @InternalAPI
    public var principalResolver: SessionPrincipalResolver<S, P>? = null

    internal var sessionTransformer: SessionTransformer<S>? = null

    internal var csrfConfig: (CSRFConfig.() -> Unit)? = null

    /**
     * Configures how the typed session scheme installs the [Sessions] plugin.
     *
     * Assign one [SessionTransportType] variant, for example `SessionTransportType.Cookie()` or
     * `SessionTransportType.HeaderId(storage)`. Only one transport applies per scheme.
     *
     * Defaults to [SessionTransportType.Cookie]. Manual setups can call `install(Sessions) { cookie(auth) }` instead of
     * configuring [transport].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.transport)
     */
    public var transport: SessionTransportType<S> = SessionTransportType.Cookie()

    /**
     * Sets a validation function for the session value.
     *
     * Return the principal of type [P] when the session is accepted, or `null` when the session is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and session value read by the
     * [Sessions] plugin.
     */
    @OptIn(InternalAPI::class)
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
     * session (`!=`). Returning the same object skips [CurrentSession.set].
     *
     * @param block transformation function called with the session value read by the
     * [Sessions] plugin.
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

    @PublishedApi
    @InternalAPI
    internal fun buildProvider(
        name: String,
        sessionKey: AttributeKey<S>,
        sessionType: KClass<S>
    ): SessionAuthenticationProvider<S> {
        val config = SessionAuthenticationProvider.Config(name, description, sessionType).apply {
            sessionName = name
        }
        val resolver = requireNotNull(principalResolver) { "Principal resolver cannot be null" }
        val transformer = sessionTransformer
        config.validate { session ->
            val routingContext = toRoutingContext()
            val effectiveSession = if (transformer != null) {
                val updatedSession = transformer(routingContext, session) ?: return@validate null
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
