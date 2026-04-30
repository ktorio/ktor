/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.reflect.*

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
@ExperimentalKtorApi
public class SessionAuthScheme<S : Any, P : Any> internal constructor(
    name: String,
    internal val sessionType: KClass<S>,
    internal val sessionTypeInfo: TypeInfo,
    principalType: KClass<P>,
    provider: AuthenticationProvider,
    onUnauthorized: UnauthorizedHandler?,
    sessionKey: AttributeKey<S>,
) : DefaultAuthScheme<P, SessionAuthenticatedContext<S, P>>(
    name = name,
    principalType = principalType,
    provider = provider,
    onUnauthorized = onUnauthorized,
    contextFactory = { defaultContext ->
        SessionAuthenticatedContext(
            defaultContext = defaultContext,
            sessionKey = sessionKey,
            sessionProviderName = name
        )
    }
) {

    override fun preinstall(route: Route) {
        try {
            route.plugin(Sessions)
        } catch (_: MissingApplicationPluginException) {
            val message = "Typed session auth scheme `$name` requires Sessions to be installed before " +
                "authenticateWith. Install it with install(Sessions) { cookie(auth) } before the typed route."
            error(message)
        }

        @OptIn(InternalAPI::class)
        val providers = route.application.attributes.getOrNull(SessionProvidersKey).orEmpty()
        val provider = providers.firstOrNull { it.name == name }
            ?: error(
                "Typed session auth scheme `$name` requires a Sessions provider named `$name` " +
                    "for session type `$sessionType`. Install it with install(Sessions) { cookie(auth) } " +
                    "before authenticateWith."
            )

        check(provider.type == sessionType) {
            "Typed session auth scheme `$name` expects session type `$sessionType`, but Sessions " +
                "provider `$name` is registered for `${provider.type}`."
        }

        super.preinstall(route)
    }
}
