/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth.apikey

import io.ktor.server.auth.AuthenticationScheme
import io.ktor.server.auth.SimpleAuthenticationScheme
import io.ktor.utils.io.*

/**
 * Creates a typed API key authentication scheme with principal type [P].
 *
 * ```kotlin
 * data class ApiKeyPrincipal(val key: String)
 *
 * val apiKeyAuth = apiKey<ApiKeyPrincipal>("api-key") {
 *     validate { apiKey ->
 *         if (apiKey == "valid") ApiKeyPrincipal(apiKey) else null
 *     }
 * }
 *
 * routing {
 *     authenticateWith(apiKeyAuth) {
 *         get("/protected") { call.respondText(call.principal.key) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.apikey.apiKey)
 *
 * @param name name that identifies the authentication scheme.
 * @param configure configures API key authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> apiKey(
    name: String,
    configure: TypedApiKeyAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedApiKeyAuthConfig<P>().apply(configure)
    val provider = typedConfig.buildProvider(name)
    return AuthenticationScheme.from(provider, typedConfig.onUnauthorized)
}
