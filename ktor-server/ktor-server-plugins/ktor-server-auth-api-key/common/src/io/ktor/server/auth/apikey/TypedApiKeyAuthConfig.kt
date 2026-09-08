/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.apikey

import io.ktor.server.application.*
import io.ktor.server.auth.UnauthorizedHandler
import io.ktor.utils.io.*

/**
 * Configures a typed API key authentication scheme with principal type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.TypedApiKeyAuthConfig)
 */
@ExperimentalKtorApi
@KtorDsl
public class TypedApiKeyAuthConfig<P : Any> @PublishedApi internal constructor() {
    /**
     * Human-readable description of this authentication scheme.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.TypedApiKeyAuthConfig.description)
     */
    public var description: String? = null

    /**
     * Header name used to read the API key.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.TypedApiKeyAuthConfig.headerName)
     */
    public var headerName: String = ApiKeyAuth.DEFAULT_HEADER_NAME

    /**
     * Default handler for authentication failures.
     *
     * A route-level `onUnauthorized` passed to [io.ktor.server.auth.authenticateWith] overrides this handler. If both are `null`, API key
     * authentication sends the default challenge described by this configuration.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.TypedApiKeyAuthConfig.onUnauthorized)
     */
    public var onUnauthorized: UnauthorizedHandler? = null

    /**
     * Sets a validation function for the API key string read from [headerName].
     *
     * Return a principal of type [P] when authentication succeeds, or `null` when the key is invalid.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.TypedApiKeyAuthConfig.validate)
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

    private var validateFn: (suspend ApplicationCall.(String) -> P?)? = null
}
