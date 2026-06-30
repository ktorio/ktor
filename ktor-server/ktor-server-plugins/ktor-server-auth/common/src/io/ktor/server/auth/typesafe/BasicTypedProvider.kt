/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth.typesafe

import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlin.jvm.JvmName

/**
 * Creates a typed Basic authentication scheme.
 *
 * The [validate][TypedBasicAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme
 * with [authenticateWith] to protect routes and access [ApplicationCall.principal] without casts.
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
): DefaultAuthScheme<P, PrincipalContext<P>> {
    val typedConfig = TypedBasicAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Bearer authentication scheme.
 *
 * The [validate][TypedBearerAuthConfig.validate] callback returns a principal of type [P]. Use the returned
 * scheme with [authenticateWith] to protect routes and access [ApplicationCall.principal] without casts.
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
): DefaultAuthScheme<P, PrincipalContext<P>> {
    val typedConfig = TypedBearerAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Form authentication scheme.
 *
 * The [validate][TypedFormAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme with
 * [authenticateWith] to protect routes and access [ApplicationCall.principal] without casts.
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
): DefaultAuthScheme<P, PrincipalContext<P>> {
    val typedConfig = TypedFormAuthConfig<P>().apply(configure)
    return DefaultAuthScheme.withDefaultContext(name, typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Session authentication scheme.
 *
 * The session value [S] is validated and mapped to a route principal [P]. Use this scheme with [authenticateWith]
 * after installing and configuring [io.ktor.server.sessions.Sessions].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.session)
 *
 * @param name name that identifies the Session authentication scheme.
 * @param configure configures Session authentication for this scheme.
 * @return a typed authentication scheme that produces principals of type [P].
 */
@ExperimentalKtorApi
@JvmName("sessionWithPrincipal")
public inline fun <reified S : Any, reified P : Any> session(
    name: String,
    configure: TypedSessionAuthConfig<S, P>.() -> Unit
): SessionAuthScheme<S, P> {
    val config = TypedSessionAuthConfig<S, P>().apply(configure)
    return SessionAuthScheme.from(
        name = name,
        sessionTypeInfo = typeInfo<S>(),
        principalType = P::class,
        config = config
    )
}

/**
 * Creates a typed Session authentication scheme where the stored session is also the route principal.
 *
 * Use the returned route context to read, update, or clear the authenticated [ApplicationCall.session] value without
 * calling `call.sessions` directly.
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
    noinline configure: TypedSessionAuthConfig<P, P>.() -> Unit
): SessionAuthScheme<P, P> = session<P, P>(name, configure)
