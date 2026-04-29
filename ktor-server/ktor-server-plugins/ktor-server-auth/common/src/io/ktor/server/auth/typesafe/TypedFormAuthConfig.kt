/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.utils.io.*

/**
 * Configures a typed Form authentication scheme.
 *
 * Unlike [FormAuthenticationProvider.Config], [validate] returns [P] so routes protected by [authenticateWith] can
 * read [principal] as the configured type.
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
     * POST parameter name used to read the username.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.userParamName)
     */
    public var userParamName: String = "user"

    /**
     * POST parameter name used to read the password.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.passwordParamName)
     */
    public var passwordParamName: String = "password"

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [authenticateWith] overrides this handler. If both are `null`, Form
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: (suspend (ApplicationCall, AuthenticationFailedCause) -> Unit)? = null

    private var validateFn: (suspend ApplicationCall.(UserPasswordCredential) -> P?)? = null

    /**
     * Sets a validation function for [UserPasswordCredential].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when credentials are invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedFormAuthConfig.validate)
     *
     * @param body validation function called for credentials extracted from form parameters.
     */
    public fun validate(body: suspend ApplicationCall.(UserPasswordCredential) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): FormAuthenticationProvider {
        val config = FormAuthenticationProvider.Config(name, description)
        config.userParamName = userParamName
        config.passwordParamName = passwordParamName
        validateFn?.let { fn -> config.validate { credential -> fn(credential) } }
        return config.build()
    }
}
