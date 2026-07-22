/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.server.routing.*
import io.ktor.utils.io.*

/**
 * Configures a typed Form authentication scheme.
 *
 * Unlike [FormAuthenticationProvider.Config], [validate] returns [P] so routes protected by [authenticateWith] can
 * read [io.ktor.server.application.ApplicationCall.principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * Form authentication responds with its default `401 Unauthorized` challenge.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedFormAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Specifies a POST parameter name used to fetch a username.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.usernameField)
     */
    public var usernameField: String = "user"

    /**
     * Specifies a POST parameter name used to fetch a password.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.passwordField)
     */
    public var passwordField: String = "password"

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Form
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    private var validateFn: (suspend RoutingContext.(UserPasswordCredential) -> P?)? = null

    /**
     * Sets a validation function for [UserPasswordCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when credentials are invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.validate)
     *
     * @param body validation function called with the current routing context and credentials extracted from form
     * parameters.
     */
    public fun validate(body: suspend RoutingContext.(UserPasswordCredential) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): FormAuthenticationProvider {
        val config = FormAuthenticationProvider.Config(name, description)
        config.userParamName = usernameField
        config.passwordParamName = passwordField
        validateFn?.let { fn -> config.validate { credential -> fn(toRoutingContext(), credential) } }
        return config.build()
    }
}
