/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalKtorApi::class, InternalAPI::class)

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.plugins.csrf.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Creates a custom session-authenticated route context.
 *
 * @param S stored session type.
 * @param P route principal type.
 * @param C authenticated route context type.
 */
public typealias SessionContextFactory<S, P, C> = (SessionContext<S, P>) -> C

/**
 * A typed Session authentication scheme.
 *
 * Use [io.ktor.server.sessions.Sessions] to configure how the session is transported or stored, for example with
 * `install(Sessions) { cookie(auth) }`.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.SessionAuthScheme)
 *
 * @param S the stored session type.
 * @param P the principal type exposed to authenticated routes.
 */
public open class SessionAuthScheme<S : Any, P : Any> internal constructor(
    name: String,
    principalType: KClass<P>,
    internal val sessionKey: AttributeKey<S>,
    internal val sessionType: KClass<S>,
    internal val sessionTypeInfo: TypeInfo,
    private val config: TypedSessionAuthConfig<S, P>,
) : DefaultAuthScheme<P, SessionContext<S, P>>(
    name = name,
    principalType = principalType,
    provider = config.buildProvider(name, sessionType, sessionKey),
    onUnauthorized = config.onUnauthorized,
    contextFactory = { base -> SessionContext(base, sessionKey, sessionProviderName = name) }
) {

    /**
     * Installs the [Sessions] plugin for this scheme on [route].
     *
     * This is used by integrations that own their route subtree and want the typed session scheme to install its
     * configured transport automatically.
     *
     * @param route route where the [Sessions] plugin should be installed.
     * @throws IllegalStateException when no transport configuration was provided.
     */
    public fun installSessionsPlugin(route: Route) {
        val transport = config.transport ?: run {
            val message = "Typed session auth scheme `$name` requires Sessions to be installed before " +
                "authenticateWith. Set transport = SessionTransport.Cookie() on the session auth config, or install " +
                "Sessions manually with install(Sessions) { cookie(auth) } before the typed route."
            error(message)
        }
        route.install(Sessions) {
            when (transport) {
                is SessionTransport.Cookie -> cookie(this@SessionAuthScheme, transport.block)
                is SessionTransport.CookieId -> cookie(this@SessionAuthScheme, transport.storage, transport.block)
                is SessionTransport.Header -> header(this@SessionAuthScheme, transport.block)
                is SessionTransport.HeaderId -> header(this@SessionAuthScheme, transport.storage, transport.block)
            }
        }
    }

    override fun preinstall(route: Route) {
        try {
            route.plugin(Sessions)
        } catch (_: MissingApplicationPluginException) {
            installSessionsPlugin(route)
        }

        config.csrfConfig?.let { configure ->
            route.install(plugin = CSRF, configure)
        }

        val providers = route.application.attributes.getOrNull(SessionProvidersKey).orEmpty()
        val provider = providers.firstOrNull { it.name == name }
            ?: error(
                "Typed session auth scheme `$name` requires a Sessions provider named `$name` " +
                    "for session type `$sessionType`. Set transport = SessionTransport.Cookie() on the session auth " +
                    "config, or install Sessions manually with install(Sessions) { cookie(auth) } before authenticateWith."
            )

        check(provider.type == sessionType) {
            "Typed session auth scheme `$name` expects session type `$sessionType`, but Sessions " +
                "provider `$name` is registered for `${provider.type}`."
        }

        super.preinstall(route)
    }

    public companion object {
        @PublishedApi
        internal fun <S : Any, P : Any> from(
            name: String,
            sessionTypeInfo: TypeInfo,
            principalType: KClass<P>,
            config: TypedSessionAuthConfig<S, P>
        ): SessionAuthScheme<S, P> {
            @Suppress("UNCHECKED_CAST")
            val sessionType = sessionTypeInfo.type as KClass<S>

            return SessionAuthScheme(
                name = name,
                config = config,
                sessionType = sessionType,
                sessionTypeInfo = sessionTypeInfo,
                principalType = principalType,
                sessionKey = AttributeKey(name = "TypesafeAuth:$name:Session", type = sessionTypeInfo),
            )
        }
    }
}
