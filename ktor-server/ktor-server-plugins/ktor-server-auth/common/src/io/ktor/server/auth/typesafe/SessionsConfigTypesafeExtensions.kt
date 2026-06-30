/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth.typesafe

import io.ktor.server.sessions.*
import io.ktor.utils.io.*

/**
 * Configures [Sessions] to pass the typed authentication session in a cookie.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.cookie)
 *
 * @param scheme typed Session authentication scheme.
 * @param block configures cookie settings, serialization, and transformations.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> SessionsConfig.cookie(
    scheme: SessionAuthScheme<S, P, *>,
    block: CookieSessionBuilder<S>.() -> Unit = {}
) {
    cookie(scheme.name, scheme.sessionTypeInfo, block)
}

/**
 * Configures [Sessions] to pass the typed authentication session ID in a cookie and store session data on the server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.cookie)
 *
 * @param scheme typed Session authentication scheme.
 * @param storage server-side session storage.
 * @param block configures cookie settings, serialization, transformations, and ID generation.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> SessionsConfig.cookie(
    scheme: SessionAuthScheme<S, P, *>,
    storage: SessionStorage,
    block: CookieIdSessionBuilder<S>.() -> Unit = {}
) {
    cookie(scheme.name, scheme.sessionTypeInfo, storage, block)
}

/**
 * Configures [Sessions] to pass the typed authentication session in a header.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.header)
 *
 * @param scheme typed Session authentication scheme.
 * @param block configures header settings, serialization, and transformations.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> SessionsConfig.header(
    scheme: SessionAuthScheme<S, P, *>,
    block: HeaderSessionBuilder<S>.() -> Unit = {}
) {
    header(scheme.name, scheme.sessionTypeInfo, block)
}

/**
 * Configures [Sessions] to pass the typed authentication session ID in a header and store session data on the server.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.header)
 *
 * @param scheme typed Session authentication scheme.
 * @param storage server-side session storage.
 * @param block configures header settings, serialization, transformations, and ID generation.
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> SessionsConfig.header(
    scheme: SessionAuthScheme<S, P, *>,
    storage: SessionStorage,
    block: HeaderIdSessionBuilder<S>.() -> Unit = {}
) {
    header(scheme.name, scheme.sessionTypeInfo, storage, block)
}

/**
 * Sets a session value for [scheme].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.typesafe.set)
 *
 * @param scheme typed Session authentication scheme.
 * @param value session value to set.
 * @throws IllegalStateException if no session provider is registered for [scheme].
 */
@ExperimentalKtorApi
public fun <S : Any, P : Any> CurrentSession.set(scheme: SessionAuthScheme<S, P, *>, value: S) {
    set(scheme.name, value)
}
