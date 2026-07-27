/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(InternalAPI::class)

package io.ktor.server.auth

import io.ktor.util.reflect.*
import io.ktor.utils.io.*

/**
 * Creates a typed Basic authentication scheme with a principal type [P].
 *
 * ```kotlin
 * data class User(val name: String)
 *
 * val userAuth = basic<User>("user-auth") {
 *     validate { credentials -> findUser(credentials.name, credentials.password) }
 * }
 *
 * routing {
 *     authenticateWith(userAuth) {
 *         get("/me") { call.respondText(call.principal.name) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.basic)
 *
 * @param name name that identifies the Basic authentication scheme.
 * @param configure configures Basic authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> basic(
    name: String,
    configure: TypedBasicAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedBasicAuthConfig<P>().apply(configure)
    return AuthenticationScheme.from(typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Bearer authentication scheme with a principal type [P].
 *
 * ```kotlin
 * data class ApiUser(val id: String)
 *
 * val bearerAuth = bearer<ApiUser>("api-bearer") {
 *     validate { token -> findUserByToken(token) }
 * }
 *
 * routing {
 *     authenticateWith(bearerAuth) {
 *         get("/api/me") { call.respondText(call.principal.id) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.bearer)
 *
 * @param name name that identifies the Bearer authentication scheme.
 * @param configure configures Bearer authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> bearer(
    name: String,
    configure: TypedBearerAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedBearerAuthConfig<P>().apply(configure)
    return AuthenticationScheme.from(typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Form authentication scheme.
 *
 * The [validate][TypedFormAuthConfig.validate] callback returns a principal of type [P]. Use the returned scheme with
 * [authenticateWith] to protect routes and access `principal` without casts.
 *
 * ```kotlin
 * data class User(val name: String)
 *
 * val formAuth = form<User>("login-form") {
 *     validate { credentials -> findUser(credentials.user, credentials.password) }
 * }
 *
 * routing {
 *     authenticateWith(formAuth) {
 *         get("/dashboard") { call.respondText(call.principal.name) }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.form)
 *
 * @param name name that identifies the Form authentication scheme.
 * @param configure configures Form authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified P : Any> form(
    name: String,
    configure: TypedFormAuthConfig<P>.() -> Unit
): SimpleAuthenticationScheme<P> {
    val typedConfig = TypedFormAuthConfig<P>().apply(configure)
    return AuthenticationScheme.from(typedConfig.buildProvider(name), typedConfig.onUnauthorized)
}

/**
 * Creates a typed Session authentication scheme with a principal type [P] and a session type [S].
 *
 * The session value [S] is validated and mapped to a route principal [P].
 * Install the scheme with [io.ktor.server.routing.Route.install] or [io.ktor.server.application.Application.install]
 * before protecting routes with [authenticateWith].
 *
 * ```kotlin
 * data class UserSession(val username: String)
 * data class User(val name: String)
 *
 * val sessionAuth = session<UserSession, User>("user-session") {
 *     validate { session -> User(session.username) }
 * }
 *
 * routing {
 *     install(sessionAuth)
 *     get("/login") {
 *         sessionAuth.setSession(UserSession("alice"))
 *         call.respondRedirect("/me")
 *     }
 *     authenticateWith(sessionAuth) {
 *         get("/me") {
 *             call.respondText("${call.session.username}:${call.principal.name}")
 *         }
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.session)
 *
 * @param name name that identifies the Session authentication scheme.
 * @param configure configures Session authentication for this scheme.
 */
@ExperimentalKtorApi
public inline fun <reified S : Any, reified P : Any> session(
    name: String,
    configure: TypedSessionAuthConfig<S, P>.() -> Unit
): SessionAuthenticationScheme<S, P> {
    val config = TypedSessionAuthConfig<S, P>().apply(configure)
    return SessionAuthenticationScheme.from(
        name = name,
        sessionTypeInfo = typeInfo<S>(),
        principalType = P::class,
        config = config
    )
}
