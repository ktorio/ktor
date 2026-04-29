/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.auth.*
import io.ktor.utils.io.*

/**
 * Creates a typed Basic authentication scheme.
 *
 * The [validate][TypedBasicAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme
 * with [authenticateWith] to protect routes and access [principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.basic)
 *
 * @param name name that identifies the Basic authentication scheme.
 * @param configure configures Basic authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> basic(
    name: String,
    configure: TypedBasicAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedBasicAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Bearer authentication scheme.
 *
 * The [authenticate][TypedBearerAuthConfig.authenticate] callback returns a principal of type [P]. Use the returned
 * scheme with [authenticateWith] to protect routes and access [principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.bearer)
 *
 * @param name name that identifies the Bearer authentication scheme.
 * @param configure configures Bearer authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> bearer(
    name: String,
    configure: TypedBearerAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedBearerAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Form authentication scheme.
 *
 * The [validate][TypedFormAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme with
 * [authenticateWith] to protect routes and access [principal] without casts.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.form)
 *
 * @param name name that identifies the Form authentication scheme.
 * @param configure configures Form authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> form(
    name: String,
    configure: TypedFormAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedFormAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Session authentication scheme.
 *
 * The session value is validated as [P] and the accepted value becomes the route [principal]. Use this scheme with
 * [authenticateWith] after installing and configuring [io.ktor.server.sessions.Sessions].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 *
 * @param name name that identifies the Session authentication scheme.
 * @param configure configures Session authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> session(
    name: String,
    configure: TypedSessionAuthConfig<P>.() -> Unit
): DefaultAuthScheme<P, DefaultAuthenticatedContext<P>> {
    val typedConfig = TypedSessionAuthConfig<P>().apply(configure)
    val provider = typedConfig.buildProvider(name, type = P::class)
    return DefaultAuthScheme.withDefaultContext(name, provider, typedConfig.onUnauthorized)
}
