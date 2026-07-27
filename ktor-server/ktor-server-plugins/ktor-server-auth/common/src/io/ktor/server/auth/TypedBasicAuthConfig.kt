/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * Configures a typed Basic authentication scheme with principal type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedBasicAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig.realm)
     */
    public var realm: String = "Ktor Server"

    /**
     * Charset used to decode credentials.
     *
     * It can be either `UTF_8` or `null`.
     * Setting `null` turns on a legacy mode (`ISO-8859-1`).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig.charset)
     */
    public var charset: Charset? = Charsets.UTF_8

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Basic
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    /**
     * Sets a validation function for [UserPasswordCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when credentials are invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.TypedBasicAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and credentials extracted from the request.
     */
    public fun validate(body: suspend RoutingContext.(UserPasswordCredential) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): BasicAuthenticationProvider {
        val config = BasicAuthenticationProvider.Config(name, description)
        config.realm = realm
        config.charset = charset
        validateFn?.let { fn -> config.validate { credential -> toRoutingContext().fn(credential) } }
        return BasicAuthenticationProvider(config)
    }

    private var validateFn: (suspend RoutingContext.(UserPasswordCredential) -> P?)? = null
}
