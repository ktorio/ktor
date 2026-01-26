/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.auth

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlin.jvm.*

/**
 * A [Route] that has an authenticated [principal] of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.AuthenticatedRoute)
 */
@JvmInline
public value class AuthenticatedRoute<P : Any>(public val route: Route)

/**
 * Creates a route that allows you to define authorization scope for application resources and
 * provides a non-null [principal] of type [P] to the handlers.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.authenticate)
 *
 * @param configurations names of authentication providers defined in the [Authentication] plugin configuration.
 * @param build a block to be executed when the route is matched.
 */
public inline fun <reified P : Any> Route.authenticate(
    vararg configurations: String? = arrayOf(null),
    crossinline build: AuthenticatedRoute<P>.() -> Unit
): Route {
    return authenticate(*configurations, optional = false) {
        build(AuthenticatedRoute(this))
    }
}

/**
 * Installs a handler into this route which is called when the route is selected for a call and
 * a principal of type [P] is present.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.handle)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.handle(
    noinline body: suspend RoutingContext.(P) -> Unit
) {
    route.handle {
        val principal = call.principal<P>()
            ?: return@handle call.respond(HttpStatusCode.Unauthorized)
        body(this, principal)
    }
}

/**
 * Builds a route to match the specified [path].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.route)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.route(
    path: String,
    crossinline build: AuthenticatedRoute<P>.() -> Unit
): Route = route.route(path) {
    AuthenticatedRoute<P>(this).build()
}

/**
 * Builds a route to match the specified HTTP [method].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.method)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.method(
    method: HttpMethod,
    crossinline build: AuthenticatedRoute<P>.() -> Unit
): Route = route.method(method) {
    AuthenticatedRoute<P>(this).build()
}

/**
 * Builds a route to match `GET` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.get)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.get(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Get) {
    handle(body)
}

/**
 * Builds a route to match `GET` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.get)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.get(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    get(body)
}

/**
 * Builds a route to match `POST` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.post)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.post(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Post) {
    handle(body)
}

/**
 * Builds a route to match `POST` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.post)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.post(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    post(body)
}

/**
 * Builds a route to match `POST` requests receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.post)
 */
@JvmName("postTyped")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.post(
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = post { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `POST` requests with the specified [path] receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.post)
 */
@JvmName("postTypedPath")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.post(
    path: String,
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = post(path) { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `PUT` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.put)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.put(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Put) {
    handle(body)
}

/**
 * Builds a route to match `PUT` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.put)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.put(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    put(body)
}

/**
 * Builds a route to match `PUT` requests receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.put)
 */
@JvmName("putTyped")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.put(
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = put { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `PUT` requests with the specified [path] receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.put)
 */
@JvmName("putTypedPath")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.put(
    path: String,
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = put(path) { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `PATCH` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.patch)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.patch(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Patch) {
    handle(body)
}

/**
 * Builds a route to match `PATCH` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.patch)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.patch(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    patch(body)
}

/**
 * Builds a route to match `PATCH` requests receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.patch)
 */
@JvmName("patchTyped")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.patch(
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = patch { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `PATCH` requests with the specified [path] receiving a request body as content of the [R] type
 * with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.patch)
 */
@JvmName("patchTypedPath")
public inline fun <reified P : Any, reified R : Any> AuthenticatedRoute<P>.patch(
    path: String,
    noinline body: suspend RoutingContext.(P, R) -> Unit
): Route = patch(path) { principal ->
    body(principal, call.receive())
}

/**
 * Builds a route to match `DELETE` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.delete)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.delete(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Delete) {
    handle(body)
}

/**
 * Builds a route to match `DELETE` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.delete)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.delete(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    delete(body)
}

/**
 * Builds a route to match `OPTIONS` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.options)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.options(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Options) {
    handle(body)
}

/**
 * Builds a route to match `OPTIONS` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.options)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.options(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    options(body)
}

/**
 * Builds a route to match `HEAD` requests with a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.head)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.head(
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = method(HttpMethod.Head) {
    handle(body)
}

/**
 * Builds a route to match `HEAD` requests with the specified [path] and a principal of type [P].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.auth.head)
 */
public inline fun <reified P : Any> AuthenticatedRoute<P>.head(
    path: String,
    noinline body: suspend RoutingContext.(P) -> Unit
): Route = route(path) {
    head(body)
}
