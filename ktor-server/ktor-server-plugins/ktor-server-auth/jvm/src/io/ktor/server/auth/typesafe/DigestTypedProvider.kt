/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.utils.io.*

/**
 * Creates a typed Digest authentication scheme.
 *
 * The [validate][TypedDigestAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme
 * with [authenticateWith] to protect routes and access [principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.digest)
 *
 * @param name name that identifies the Digest authentication scheme.
 * @param configure configures Digest authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> digest(
    name: String,
    configure: TypedDigestAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedDigestAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}
