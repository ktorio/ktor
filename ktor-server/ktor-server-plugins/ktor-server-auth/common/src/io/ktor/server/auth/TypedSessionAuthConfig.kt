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
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SessionPrincipalResolver)
 */
public fun interface SessionPrincipalResolver<S, P> {
    public suspend fun RoutingContext.resolvePrincipal(session: S): P?
}

/**
 * Transforms a session value before principal resolution.
 *
 * Return the session value that should be validated for the current call, or `null` to reject the session.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SessionTransformer)
 */
public fun interface SessionTransformer<S> {
    public suspend fun RoutingContext.transform(currentSession: S): S?
}

/**
 * Configures a typed Session authentication scheme with principal type [P] and session type [S].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig)
 */
@KtorDsl
@ExperimentalKtorApi
public open class TypedSessionAuthConfig<S : Any, P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Session
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    /**
     * Configures how the typed session scheme installs the [Sessions] plugin.
     *
     * Assign one [SessionTransportType] variant, for example `SessionTransportType.Cookie()` or
     * `SessionTransportType.HeaderId(storage)`. Only one transport applies per scheme.
     *
     * Defaults to [SessionTransportType.Cookie]. Manual setups can call `install(Sessions) { cookie(auth) }` instead of
     * configuring [transport].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.transport)
     */
    public var transport: SessionTransportType<S> = SessionTransportType.Cookie()

    /**
     * Sets a validation function for the session value.
     *
     * Return the principal of type [P] when the session is accepted, or `null` when the session is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and session value read by the
     * [Sessions] plugin.
     */
    public fun validate(body: SessionPrincipalResolver<S, P>?) {
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
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.transformSession)
     */
    public fun transformSession(block: SessionTransformer<S>) {
        sessionTransformer = block
    }

    /**
     * Configures CSRF protection for routes authenticated with this session scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedSessionAuthConfig.csrfProtection)
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
        config.validate { currentSession ->
            val routingContext = toRoutingContext()

            val effectiveSession = if (transformer != null) {
                val updatedSession = with(transformer) {
                    routingContext.transform(currentSession) ?: return@validate null
                }
                if (updatedSession != currentSession) {
                    sessions.set(name, updatedSession)
                }
                updatedSession
            } else {
                currentSession
            }

            val principal = with(resolver) {
                routingContext.resolvePrincipal(effectiveSession)
            }
            if (principal != null) {
                attributes.put(sessionKey, effectiveSession)
            }
            principal
        }
        return config.buildProvider()
    }

    internal var principalResolver: SessionPrincipalResolver<S, P>? = null
    private var sessionTransformer: SessionTransformer<S>? = null
    internal var csrfConfig: (CSRFConfig.() -> Unit)? = null
}
