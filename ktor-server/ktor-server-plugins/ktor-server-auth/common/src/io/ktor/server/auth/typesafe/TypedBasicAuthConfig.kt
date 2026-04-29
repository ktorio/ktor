/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*

/**
 * Configures a typed Basic authentication scheme.
 *
 * Unlike [BasicAuthenticationProvider.Config], [validate] returns [P] so routes protected by [authenticateWith] can
 * read [principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * Basic authentication responds with a `WWW-Authenticate: Basic` challenge that includes [realm] and [charset].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedBasicAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Realm passed in the `WWW-Authenticate` header.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig.realm)
     */
    public var realm: String = "Ktor Server"

    /**
     * Charset used to decode credentials.
     *
     * It can be either `UTF_8` or `null`.
     * Setting `null` turns on a legacy mode (`ISO-8859-1`).
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig.charset)
     */
    public var charset: Charset? = Charsets.UTF_8

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Basic
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var validateFn: (suspend ApplicationCall.(UserPasswordCredential) -> P?)? = null

    /**
     * Sets a validation function for [UserPasswordCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when credentials are invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedBasicAuthConfig.validate)
     *
     * @param body validation function called for credentials extracted from the request.
     */
    public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): BasicAuthenticationProvider {
        val config = BasicAuthenticationProvider.Config(name, description)
        config.realm = realm
        config.charset = charset
        validateFn?.let { fn -> config.validate { credential -> fn(credential) } }
        return BasicAuthenticationProvider(config)
    }
}
