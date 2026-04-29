/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.utils.io.*

/**
 * Creates a typed API key authentication scheme.
 *
 * The [validate][TypedApiKeyAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme
 * with [authenticateWith] to protect routes and access [principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.apiKey)
 *
 * @param name name that identifies the API key authentication scheme.
 * @param configure configures API key authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> apiKey(
    name: String,
    configure: TypedApiKeyAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedApiKeyAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}
