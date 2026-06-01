/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.jwt.typesafe

import io.ktor.server.auth.typesafe.DefaultAuthScheme
import io.ktor.server.auth.typesafe.DefaultAuthenticatedContext
import io.ktor.utils.io.*

/**
 * Creates a typed JWT authentication scheme.
 *
 * The [validate][TypedJwtAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme with
 * [io.ktor.server.auth.typesafe.authenticateWith] to protect routes and access [io.ktor.server.auth.typesafe.principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.jwt)
 *
 * @param name name that identifies the JWT authentication scheme.
 * @param configure configures JWT authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> jwt(
    name: String,
    configure: TypedJwtAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedJwtAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.Companion.withDefaultContext(
        name,
        typedConfig.buildProvider(name),
        typedConfig.onUnauthorized
    )
}
