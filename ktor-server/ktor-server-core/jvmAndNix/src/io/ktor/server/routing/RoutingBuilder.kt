/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

@file:Suppress("unused")

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*

/**
 * Builds a route to match the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.route(path: String, build: RoutingBuilder.() -> Unit): RoutingBuilder =
    createRouteFromPath(path).apply(build)

/**
 * Builds a route to match the specified HTTP [method] and [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.route(path: String, method: HttpMethod, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = HttpMethodRouteSelector(method)
    return createRouteFromPath(path).createChild(selector).apply(build)
}

/**
 * Builds a route to match the specified HTTP [method].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.method(method: HttpMethod, body: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = HttpMethodRouteSelector(method)
    return createChild(selector).apply(body)
}

/**
 * Builds a route to match a parameter with the specified [name] and [value].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.param(name: String, value: String, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = ConstantParameterRouteSelector(name, value)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match a parameter with the specified [name] and captures its value.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.param(name: String, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = ParameterRouteSelector(name)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to capture an optional parameter with specified [name], if it exists.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.optionalParam(name: String, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = OptionalParameterRouteSelector(name)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match a header with the specified [name] and [value].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.header(name: String, value: String, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = HttpHeaderRouteSelector(name, value)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match requests with the [HttpHeaders.Accept] header matching the specified [contentType].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.accept(contentType: ContentType, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    val selector = HttpAcceptRouteSelector(contentType)
    return createChild(selector).apply(build)
}

/**
 * Builds a route to match requests with the [HttpHeaders.ContentType] header matching the specified [contentType].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.contentType(contentType: ContentType, build: RoutingBuilder.() -> Unit): RoutingBuilder {
    return header(HttpHeaders.ContentType, "${contentType.contentType}/${contentType.contentSubtype}", build)
}

/**
 * Builds a route to match `GET` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.get(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `GET` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.get(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.post(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `POST` requests receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("postTyped")
public inline fun <reified R : Any> RoutingBuilder.post(
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = post {
    body(call.receive())
}

/**
 * Builds a route to match `POST` requests with the specified [path] receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("postTypedPath")
public inline fun <reified R : Any> RoutingBuilder.post(
    path: String,
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = post(path) {
    body(call.receive())
}

/**
 * Builds a route to match `POST` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.post(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.head(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.head(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.put(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.put(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("putTyped")
public inline fun <reified R : Any> RoutingBuilder.put(
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = put {
    body(call.receive())
}

/**
 * Builds a route to match `PUT` requests with the specified [path] receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("putTypedPath")
public inline fun <reified R : Any> RoutingBuilder.put(
    path: String,
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = put(path) {
    body(call.receive())
}

/**
 * Builds a route to match `PATCH` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.patch(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Patch) { handle(body) }
}

/**
 * Builds a route to match `PATCH` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.patch(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Patch) { handle(body) }
}

/**
 * Builds a route to match `PATCH` requests receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("patchTyped")
public inline fun <reified R : Any> RoutingBuilder.patch(
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = patch {
    body(call.receive())
}

/**
 * Builds a route to match `PATCH` requests with the specified [path] receiving a request body as content of the [R] type.
 * @see [Application.routing]
 */
@KtorDsl
@JvmName("patchTypedPath")
public inline fun <reified R : Any> RoutingBuilder.patch(
    path: String,
    crossinline body: suspend RoutingContext.(R) -> Unit
): RoutingBuilder = patch(path) {
    body(call.receive())
}

/**
 * Builds a route to match `DELETE` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.delete(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.delete(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with the specified [path].
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.options(path: String, body: RoutingHandler): RoutingBuilder {
    return route(path, HttpMethod.Options) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests.
 * @see [Application.routing]
 */
@KtorDsl
public fun RoutingBuilder.options(body: RoutingHandler): RoutingBuilder {
    return method(HttpMethod.Options) { handle(body) }
}

/**
 * Creates a routing entry for specified path.
 */
public fun RoutingBuilder.createRouteFromPath(path: String): RoutingBuilder {
    val parts = RoutingPath.parse(path).parts
    var current: RoutingBuilder = this
    for (index in parts.indices) {
        val (value, kind) = parts[index]
        val selector = when (kind) {
            RoutingPathSegmentKind.Parameter -> PathSegmentSelectorBuilder.parseParameter(value)
            RoutingPathSegmentKind.Constant -> PathSegmentSelectorBuilder.parseConstant(value)
        }
        // there may already be entry with same selector, so join them
        current = current.createChild(selector)
    }
    if (path.endsWith("/")) {
        current = current.createChild(TrailingSlashRouteSelector)
    }
    return current
}

/**
 * A helper object for building instances of [RouteSelector] from path segments.
 */
public object PathSegmentSelectorBuilder {
    /**
     * Builds a [RouteSelector] to match a path segment parameter with a prefix/suffix and name.
     */
    public fun parseParameter(value: String): RouteSelector {
        val prefixIndex = value.indexOf('{')
        val suffixIndex = value.lastIndexOf('}')

        val prefix = if (prefixIndex == 0) null else value.substring(0, prefixIndex)
        val suffix = if (suffixIndex == value.length - 1) null else value.substring(suffixIndex + 1)

        val signature = value.substring(prefixIndex + 1, suffixIndex)
        return when {
            signature.endsWith("?") -> PathSegmentOptionalParameterRouteSelector(signature.dropLast(1), prefix, suffix)
            signature.endsWith("...") -> {
                if (suffix != null && suffix.isNotEmpty()) {
                    throw IllegalArgumentException("Suffix after tailcard is not supported")
                }
                PathSegmentTailcardRouteSelector(signature.dropLast(3), prefix ?: "")
            }
            else -> PathSegmentParameterRouteSelector(signature, prefix, suffix)
        }
    }

    /**
     * Builds a [RouteSelector] to match a path segment parameter with a prefix/suffix, name, and trailing slash, if any.
     */
    @Deprecated(
        "hasTrailingSlash is not used anymore. This is going to be removed",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("parseParameter(value)")
    )
    @Suppress("UNUSED_PARAMETER")
    public fun parseParameter(value: String, hasTrailingSlash: Boolean): RouteSelector = parseParameter(value)

    /**
     * Builds a [RouteSelector] to match a constant or wildcard segment parameter.
     */
    public fun parseConstant(value: String): RouteSelector = when (value) {
        "*" -> PathSegmentWildcardRouteSelector
        else -> PathSegmentConstantRouteSelector(value)
    }

    /**
     * Builds a [RouteSelector] to match a constant or wildcard segment parameter and trailing slash, if any.
     */
    @Deprecated(
        "hasTrailingSlash is not used anymore. This is going to be removed",
        level = DeprecationLevel.ERROR,
        replaceWith = ReplaceWith("parseConstant(value)")
    )
    @Suppress("UNUSED_PARAMETER")
    public fun parseConstant(value: String, hasTrailingSlash: Boolean): RouteSelector = parseConstant(value)

    /**
     * Parses a name out of segment specification.
     */
    public fun parseName(value: String): String {
        val prefix = value.substringBefore('{', "")
        val suffix = value.substringAfterLast('}', "")
        val signature = value.substring(prefix.length + 1, value.length - suffix.length - 1)
        return when {
            signature.endsWith("?") -> signature.dropLast(1)
            signature.endsWith("...") -> signature.dropLast(3)
            else -> signature
        }
    }
}
