/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*

/**
 * Configures a typed Bearer authentication scheme.
 *
 * Unlike [BearerAuthenticationProvider.Config], [authenticate] returns [P] so routes protected by [authenticateWith]
 * can read [principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * Bearer authentication responds with a `WWW-Authenticate` challenge for the default authentication scheme (`Bearer`
 * unless changed by [authSchemes]) and the optional [realm].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedBearerAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Optional Bearer realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.realm)
     */
    public var realm: String? = null

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Bearer
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var authenticateFn: (suspend ApplicationCall.(BearerTokenCredential) -> P?)? = null
    private var authHeaderFn: ((ApplicationCall) -> HttpAuthHeader?)? = null
    private var defaultScheme: String? = null
    private var additionalSchemes: List<String>? = null

    /**
     * Exchanges a bearer token for a principal of type [P].
     *
     * Return `null` when the token is not accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.authenticate)
     *
     * @param body authentication function called with the extracted [BearerTokenCredential].
     */
    public fun authenticate(body: suspend ApplicationCall.(BearerTokenCredential) -> P?) {
        authenticateFn = body
    }

    /**
     * Configures how to retrieve an HTTP authentication header.
     *
     * By default, Bearer authentication parses the `Authorization` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.authHeader)
     *
     * @param block returns an authentication header for the call, or `null` when no header is available.
     */
    public fun authHeader(block: (ApplicationCall) -> HttpAuthHeader?) {
        authHeaderFn = block
    }

    /**
     * Configures accepted authentication schemes.
     *
     * By default, only the `Bearer` scheme is accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBearerAuthConfig.authSchemes)
     *
     * @param defaultScheme scheme used in the default challenge.
     * @param additionalSchemes additional schemes accepted when validating the request.
     */
    public fun authSchemes(
        defaultScheme: String = io.ktor.http.auth.AuthScheme.Bearer,
        vararg additionalSchemes: String
    ) {
        this.defaultScheme = defaultScheme
        this.additionalSchemes = additionalSchemes.toList()
    }

    @PublishedApi
    internal fun buildProvider(name: String): BearerAuthenticationProvider {
        val config = BearerAuthenticationProvider.Config(name, description)
        realm?.let { config.realm = it }
        authenticateFn?.let { fn -> config.authenticate { credential -> fn(credential) } }
        authHeaderFn?.let { config.authHeader(it) }
        defaultScheme?.let { ds ->
            config.authSchemes(ds, *additionalSchemes!!.toTypedArray())
        }
        return config.build()
    }
}
