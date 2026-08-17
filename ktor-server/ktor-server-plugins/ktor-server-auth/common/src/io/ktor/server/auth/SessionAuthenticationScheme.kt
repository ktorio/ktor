/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.server.auth

import io.ktor.server.application.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.sessions.header
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * A typed Session authentication scheme.
 *
 * Use [Sessions] to configure how the session is transported or stored, for example, with
 * `install(Sessions) { cookie(auth) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SessionAuthenticationScheme)
 *
 * @param S the stored session type.
 * @param P the principal type exposed to authenticated routes.
 */
public typealias SessionAuthenticationScheme<S, P> = AuthenticationScheme<P, SessionContext<S>>

internal class SessionAuthenticationSchemeExtension<S : Any>(
    val sessionTypeInfo: TypeInfo,
    val sessionKey: AttributeKey<S>,
    val config: TypedSessionAuthConfig<S, *>,
    val name: String,
) : AuthenticationSchemeExtension<SessionContext<S>> {

    override val context: SessionContext<S> = SessionContext(this)

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
    }

    fun SessionsConfig.applyTransport() {
        when (val transport = config.transport) {
            is SessionTransportType.Cookie -> cookie(name, sessionTypeInfo, transport.block)

            is SessionTransportType.CookieId ->
                cookie(name, sessionTypeInfo, transport.storage, transport.block)

            is SessionTransportType.Header -> header(name, sessionTypeInfo, transport.block)

            is SessionTransportType.HeaderId ->
                header(name, sessionTypeInfo, transport.storage, transport.block)
        }
    }

    private fun raiseInvalidSessionsConfiguration(): Nothing {
        error(
            "Typed session auth scheme `$name` requires Sessions to be installed " +
                "before authenticateWith. " +
                "Install Sessions manually with Route.install(SessionAuthenticationScheme<*, *>) " +
                "before the typed route or configure Sessions with SessionsConfig.applyTransport()."
        )
    }
}

@PublishedApi
internal fun <S : Any, P : Any> createSessionAuthenticationScheme(
    name: String,
    principalType: KClass<P>,
    sessionTypeInfo: TypeInfo,
    config: TypedSessionAuthConfig<S, P>
): SessionAuthenticationScheme<S, P> {
    val sessionKey = AttributeKey<S>(name = "TypesafeAuth:$name:Session", sessionTypeInfo)

    @Suppress("UNCHECKED_CAST")
    val provider = config.buildProvider(name, sessionKey, sessionTypeInfo.type as KClass<S>)
    val extension = SessionAuthenticationSchemeExtension(
        sessionTypeInfo = sessionTypeInfo,
        sessionKey = sessionKey,
        config = config,
        name = name,
    )
    return AuthenticationScheme(provider, principalType, config.onUnauthorized, extension)
}

/**
 * Applies this scheme's configured [TypedSessionAuthConfig.transport] to a [SessionsConfig].
 *
 * Called automatically by [Route.install] when installing a [SessionAuthenticationScheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.applyTransport)
 *
 * @this typed session authentication scheme whose transport configuration is applied.
 */
@ExperimentalKtorApi
context(pluginConfig: SessionsConfig)
public fun <S : Any> SessionAuthenticationScheme<S, *>.applyTransport() {
    with(extension.context.extension) { pluginConfig.applyTransport() }
}

/**
 * Installs the [Sessions] plugin for [session] on this route.
 *
 * This is used by integrations that own their route subtree and want the typed session scheme to install its
 * configured transport automatically.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.install)
 *
 * @param session typed session authentication scheme whose transport configuration is applied.
 * @throws IllegalStateException when Sessions cannot be installed for this scheme.
 */
@ExperimentalKtorApi
public fun <S : Any> Route.install(session: SessionAuthenticationScheme<S, *>) {
    install(Sessions) { session.applyTransport() }
}

/**
 * Installs the [Sessions] plugin for [sessions] at the application routing root.
 *
 * Equivalent to `application.routing { install(sessions) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.install)
 *
 * @param sessions typed session authentication scheme whose transport configuration is applied.
 */
@ExperimentalKtorApi
public fun <S : Any> Application.install(sessions: SessionAuthenticationScheme<S, *>) {
    routing { install(sessions) }
}

/**
 * Sets a session value for this scheme.
 *
 * To clear a session from a non-authenticated route, use `call.sessions.clear(name)` on the [Sessions] plugin.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SessionAuthenticationScheme.setSession)
 *
 * @this typed Session authentication scheme.
 * @param value session value to set.
 * @throws IllegalStateException if no session provider is registered for this scheme.
 */
@ExperimentalKtorApi
context(context: RoutingContext)
public fun <S : Any, P : Any> SessionAuthenticationScheme<S, P>.setSession(value: S): Unit =
    context.call.sessions.set(name, value)

/**
 * Clears a session for this scheme.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.SessionAuthenticationScheme.clearSession)
 *
 * @this typed Session authentication scheme.
 */
@ExperimentalKtorApi
context(context: RoutingContext)
public fun <S : Any, P : Any> SessionAuthenticationScheme<S, P>.clearSession(): Unit =
    context.call.sessions.clear(name)
