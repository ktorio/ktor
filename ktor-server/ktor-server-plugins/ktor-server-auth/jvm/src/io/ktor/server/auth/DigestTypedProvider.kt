/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth

import io.ktor.utils.io.*

/**
 * Creates a typed Digest authentication scheme.
 *
 * ```kotlin
 * data class User(val name: String)
 *
 * val digestAuth = digest<User>("digest-auth") {
 *     realm = "My Realm"
 *     digestProvider { userName, realm, algorithm ->
 *         computeHa1(userName, realm, passwordFor(userName))
 *     }
 *     validate { credential -> User(credential.userName) }
 * }
 *
 * routing {
 *     authenticateWith(digestAuth) {
 *         get("/resource") { call.respondText(call.principal.name) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.digest)
 *
 * @param name identifier for this scheme.
 * @param configure configures Digest authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> digest(
    name: String,
    configure: TypedDigestAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedDigestAuthConfig<P>().apply(configure)
    return AuthenticationScheme.from(typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}
