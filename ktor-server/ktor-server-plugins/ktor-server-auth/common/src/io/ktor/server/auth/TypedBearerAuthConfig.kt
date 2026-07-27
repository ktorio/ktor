/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.auth.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*

/**
 * Configures a typed Bearer authentication scheme with principal type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedBearerAuthConfig<P : Any> @InternalAPI constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Optional Bearer realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.realm)
     */
    public var realm: String? = null

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Bearer
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    /**
     * Exchanges a bearer token for a principal of type [P].
     *
     * Return `null` when the token is not accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and extracted
     * [BearerTokenCredential].
     */
    public fun validate(body: suspend RoutingContext.(BearerTokenCredential) -> P?) {
        validateFn = body
    }

    /**
     * Configures how to retrieve an HTTP authentication header.
     *
     * By default, Bearer authentication parses the `Authorization` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.authHeader)
     *
     * @param block returns an authentication header for the call, or `null` when no header is available.
     */
    public fun authHeader(block: RoutingContext.() -> HttpAuthHeader?) {
        authHeaderFn = block
    }

    /**
     * Configures accepted authentication schemes.
     *
     * By default, only the `Bearer` scheme is accepted.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBearerAuthConfig.authSchemes)
     *
     * @param defaultScheme scheme used in the default challenge.
     * @param additionalSchemes additional schemes accepted when validating the request.
     */
    public fun authSchemes(
        defaultScheme: String = AuthScheme.Bearer,
        vararg additionalSchemes: String
    ) {
        this.defaultScheme = defaultScheme
        this.additionalSchemes = additionalSchemes.toList()
    }

    @PublishedApi
    internal fun buildProvider(name: String): BearerAuthenticationProvider {
        val config = BearerAuthenticationProvider.Config(name, description)
        realm?.let { config.realm = it }
        validateFn?.let { fn -> config.authenticate { credential -> fn(toRoutingContext(), credential) } }
        authHeaderFn?.let { fn -> config.authHeader { call -> fn(call.toRoutingContext()) } }
        defaultScheme?.let { ds ->
            config.authSchemes(ds, *additionalSchemes!!.toTypedArray())
        }
        return config.build()
    }

    private var validateFn: (suspend RoutingContext.(BearerTokenCredential) -> P?)? = null
    private var authHeaderFn: (RoutingContext.() -> HttpAuthHeader?)? = null
    private var defaultScheme: String? = null
    private var additionalSchemes: List<String>? = null
}
