/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth.jwt

import io.ktor.server.auth.*
import io.ktor.utils.io.*

/**
 * Creates a typed JWT authentication scheme with principal type [P].
 *
 * ```kotlin
 * data class JwtUser(val name: String)
 *
 * val jwtAuth = jwt<JwtUser>("jwt-auth") {
 *     verifier(issuer = "https://issuer.example", audience = "my-audience", algorithm = Algorithm.HMAC256("secret"))
 *     validate { credential ->
 *         if (credential.audience.contains("my-audience")) {
 *             JwtUser(credential.payload.subject)
 *         } else {
 *             null
 *         }
 *     }
 * }
 *
 * routing {
 *     authenticateWith(jwtAuth) {
 *         get("/profile") { call.respondText(call.principal.name) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.jwt.jwt)
 *
 * @param name name that identifies the JWT authentication scheme.
 * @param configure configures JWT authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> jwt(
    name: String,
    configure: TypedJwtAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedJwtAuthConfig<P>().apply(configure)
    val provider = typedConfig.buildProvider(name)
    return AuthenticationScheme.from(provider, typedConfig.onUnauthorized)
}
