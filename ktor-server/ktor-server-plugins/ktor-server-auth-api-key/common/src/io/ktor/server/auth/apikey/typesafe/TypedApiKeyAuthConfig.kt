/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.apikey.typesafe

import io.ktor.server.application.*
import io.ktor.server.auth.apikey.*
import io.ktor.server.auth.typesafe.UnauthorizedHandler
import io.ktor.utils.io.*

/**
 * Configures a typed API key authentication scheme.
 *
 * Unlike [ApiKeyAuthenticationProvider.Configuration], [validate] returns [P] so routes protected by
 * [io.ktor.server.auth.typesafe.authenticateWith] can read [io.ktor.server.auth.typesafe.principal] as the configured type.
 *
 * This config does not expose provider-level `challenge`. Set [onUnauthorized] or pass `onUnauthorized` to
 * [io.ktor.server.auth.typesafe.authenticateWith] to customize failure responses.
 *
 * Challenge strategy: a route-level `onUnauthorized` is used first, then [onUnauthorized]. If neither is configured,
 * API key authentication responds with `401 Unauthorized` and uses the scheme name as the authentication challenge key.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedApiKeyAuthConfig)
 *
 * @param P the principal type produced by this scheme.
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedApiKeyAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedApiKeyAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Header name used to read the API key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedApiKeyAuthConfig.headerName)
     */
    public var headerName: String = ApiKeyAuth.DEFAULT_HEADER_NAME

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [io.ktor.server.auth.typesafe.authenticateWith] overrides this handler. If both are `null`, API key
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedApiKeyAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    private var validateFn: (suspend ApplicationCall.(String) -> P?)? = null

    /**
     * Sets a validation function for the API key string read from [headerName].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when the key is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.TypedApiKeyAuthConfig.validate)
     *
     * @param body validation function called with the API key header value.
     */
    public fun validate(body: suspend ApplicationCall.(String) -> P?) {
        validateFn = body
    }

    @PublishedApi
    internal fun buildProvider(name: String): ApiKeyAuthenticationProvider {
        val config = ApiKeyAuthenticationProvider.Configuration(name, description)
        config.headerName = headerName
        config.authScheme = name
        validateFn?.let { fn -> config.validate { apiKey -> fn(apiKey) } }
        return ApiKeyAuthenticationProvider(config)
    }
}
