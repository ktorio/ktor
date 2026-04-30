/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlin.reflect.KClass

/**
 * Configures a typed Session authentication scheme.
 *
 * Unlike [SessionAuthenticationProvider.Config], [validate] returns [P] from a stored session value [S], so routes
 * protected by [authenticateWith] can read [principal] as [P] and [session] as [S].
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
public class TypedSessionAuthConfig<S : Any, P : Any> @PublishedApi internal constructor() {
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
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var validateFn: (suspend ApplicationCall.(S) -> P?)? = null

    /**
     * Sets a validation function for the session value.
     *
     * Return the principal of type [P] when the session is accepted, or `null` when the session is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedSessionAuthConfig.validate)
     *
     * @param body validation function called with the session value read by the [io.ktor.server.sessions.Sessions]
     * plugin.
     */
    public fun validate(body: suspend ApplicationCall.(S) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(
        name: String,
        sessionType: KClass<S>,
        sessionKey: AttributeKey<S>
    ): SessionAuthenticationProvider<S> {
        val config = SessionAuthenticationProvider.Config(name, description, sessionType)
        config.sessionName = name
        validateFn?.let { fn ->
            config.validate { session ->
                val principal = fn(session)
                if (principal != null) {
                    attributes.put(sessionKey, session)
                }
                principal
            }
        }
        return config.buildProvider()
    }
}
