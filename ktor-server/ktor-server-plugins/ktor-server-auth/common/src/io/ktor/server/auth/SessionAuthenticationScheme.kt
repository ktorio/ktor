/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class, InternalKtorSubclassing::class)

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sessions.header
import io.ktor.util.*
import io.ktor.util.annotations.InternalKtorSubclassing
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Creates a custom session-authenticated route context.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionContextFactory)
 *
 * @param S stored session type.
 * @param P route principal type.
 * @param C authenticated route context type.
 */
public typealias SessionContextFactory<S, P, C> = (SessionContext<S, P>) -> C

/**
 * A typed Session authentication scheme.
 *
 * Use [Sessions] to configure how the session is transported or stored, for example with
 * `install(Sessions) { cookie(auth) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticationScheme)
 *
 * @param S the stored session type.
 * @param P the principal type exposed to authenticated routes.
 */
@ExperimentalKtorApi
public class SessionAuthenticationScheme<S : Any, P : Any> internal constructor(
    principalType: KClass<P>,
    provider: SessionAuthenticationProvider<S>,
    internal val sessionTypeInfo: TypeInfo,
    internal val sessionKey: AttributeKey<S>,
    internal val config: TypedSessionAuthConfig<S, P>,
) : AuthenticationScheme<P, SessionContext<S, P>>(
    provider = provider,
    principalType = principalType,
    onUnauthorized = config.onUnauthorized,
    anonymousFactory = null,
    contextFactory = { SessionContext(it.principalKey, sessionKey, sessionProviderName = checkNotNull(provider.name)) }
) {
    private fun raiseInvalidSessionsConfiguration(): Nothing {
        error(
            "Typed session auth scheme `$name` requires Sessions to be installed before authenticateWith. " +
                "Install Sessions manually with Route.install(SessionAuthenticationScheme<*, *>) " +
                "before the typed route or configure Sessions with SessionsConfig.applyTransport()."
        )
    }

    override fun preinstallAt(route: Route) {
        try {
            route.plugin(Sessions)
        } catch (_: MissingApplicationPluginException) {
            raiseInvalidSessionsConfiguration()
        }

        config.csrfConfig?.let { configure ->
            route.install(plugin = CSRF, configure)
        }

        val providers = route.application.attributes.getOrNull(SessionProvidersKey).orEmpty()
        providers.firstOrNull { it.name == name && it.type == sessionTypeInfo.type }
            ?: raiseInvalidSessionsConfiguration()

        super.preinstallAt(route)
    }

    public companion object {
        @PublishedApi
        internal fun <S : Any, P : Any> from(
            name: String,
            principalType: KClass<P>,
            sessionTypeInfo: TypeInfo,
            config: TypedSessionAuthConfig<S, P>
        ): SessionAuthenticationScheme<S, P> {
            val sessionKey = AttributeKey<S>(name = "TypesafeAuth:$name:Session", sessionTypeInfo)

            @Suppress("UNCHECKED_CAST")
            val provider = config.buildProvider(name, sessionKey, sessionTypeInfo.type as KClass<S>)
            return SessionAuthenticationScheme(principalType, provider, sessionTypeInfo, sessionKey, config)
        }
    }
}

/**
 * Applies this scheme's configured [TypedSessionAuthConfig.transport] to a [SessionsConfig].
 *
 * Called automatically by [Route.install] when installing a [SessionAuthenticationScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.applyTransport)
 */
@ExperimentalKtorApi
context(pluginConfig: SessionsConfig)
public fun SessionAuthenticationScheme<*, *>.applyTransport() {
    when (val transport = config.transport) {
        is SessionTransportType.Cookie -> pluginConfig.cookie(name, sessionTypeInfo, transport.block)
        is SessionTransportType.CookieId -> pluginConfig.cookie(name, transport.storage, transport.block)
        is SessionTransportType.Header -> pluginConfig.header(name, sessionTypeInfo, transport.block)
        is SessionTransportType.HeaderId -> pluginConfig.header(name, transport.storage, transport.block)
    }
}

/**
 * Installs the [Sessions] plugin for [sessions] on this route.
 *
 * This is used by integrations that own their route subtree and want the typed session scheme to install its
 * configured transport automatically.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param sessions typed session authentication scheme whose transport configuration is applied.
 * @throws IllegalStateException when no transport configuration was provided.
 */
@ExperimentalKtorApi
public fun Route.install(sessions: SessionAuthenticationScheme<*, *>) {
    install(Sessions) { sessions.applyTransport() }
}

/**
 * Installs the [Sessions] plugin for [sessions] at the application routing root.
 *
 * Equivalent to `application.routing { install(sessions) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.install)
 *
 * @param sessions typed session authentication scheme whose transport configuration is applied.
 */
@ExperimentalKtorApi
public fun Application.install(sessions: SessionAuthenticationScheme<*, *>) {
    routing { install(sessions) }
}

/**
 * Sets a session value for this scheme.
 *
 * To clear a session from a non-authenticated route, use `call.sessions.clear(name)` on the [Sessions] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthenticationScheme.setSession)
 *
 * @this typed Session authentication scheme.
 * @param value session value to set.
 * @throws IllegalStateException if no session provider is registered for this scheme.
 */
@ExperimentalKtorApi
context(context: RoutingContext)
public fun <S : Any, P : Any> SessionAuthenticationScheme<S, P>.setSession(value: S): Unit =
    context.call.sessions.set(name, value)
