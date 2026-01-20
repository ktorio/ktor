/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.routing

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import kotlin.jvm.JvmName

/**
 * Builds a route to match the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * route(Regex("/(?<number>\\d+)")) {
 *     get("/hello") {
 *         val number = call.parameters["number"]
 *         ...
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.route)
 */
public fun Route.route(path: Regex, build: Route.() -> Unit): Route =
    createRouteFromRegexPath(path).apply(build)

/**
 * Builds a route to match the specified HTTP [method] and regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * route(Regex("/(?<name>.+)/hello"), HttpMethod.Get) {
 *     handle {
 *         val name = call.parameters["name"]
 *         ...
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.route)
 */
public fun Route.route(path: Regex, method: HttpMethod, build: Route.() -> Unit): Route {
    val selector = HttpMethodRouteSelector(method)
    return createRouteFromRegexPath(path).createChild(selector).apply(build)
}

/**
 * Builds a route to match `GET` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * get(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.get)
 */
public fun Route.get(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * post(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.post)
 */
public fun Route.post(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with the specified regex [path] receiving a request body as content of the [R] type.
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * post<String>(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.post)
 */
@JvmName("postTypedPath")
public inline fun <reified R : Any> Route.post(
    path: Regex,
    crossinline body: suspend RoutingContext.(R) -> Unit
): Route = post(path) {
    body(call.receive())
}

/**
 * Builds a route to match `HEAD` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * head(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.head)
 */
public fun Route.head(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * put(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.put)
 */
public fun Route.put(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with the specified regex [path] receiving a request body as content of the [R] type.
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * put<String>(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.put)
 */
@JvmName("putTypedPath")
public inline fun <reified R : Any> Route.put(
    path: Regex,
    crossinline body: suspend RoutingContext.(R) -> Unit
): Route = put(path) {
    body(call.receive())
}

/**
 * Builds a route to match `PATCH` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * patch(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.patch)
 */
public fun Route.patch(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Patch) { handle(body) }
}

/**
 * Builds a route to match `PATCH` requests with the specified regex [path] receiving a request body as content of the [R] type.
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * patch<String>(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.patch)
 */
@JvmName("patchTypedPath")
public inline fun <reified R : Any> Route.patch(
    path: Regex,
    crossinline body: suspend RoutingContext.(R) -> Unit
): Route = patch(path) {
    body(call.receive())
}

/**
 * Builds a route to match `DELETE` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * delete(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.delete)
 */
public fun Route.delete(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * options(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.options)
 */
public fun Route.options(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Options) { handle(body) }
}

/**
 * Builds a route to match `QUERY` requests with the specified regex [path].
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * query(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.query)
 */
public fun Route.query(path: Regex, body: RoutingHandler): Route {
    return route(path, HttpMethod.Query) { handle(body) }
}

/**
 * Builds a route to match `QUERY` requests with the specified regex [path] receiving a request body as content of the [R] type.
 * Named parameters from regex can be accessed via [ApplicationCall.parameters].
 *
 * Example:
 * ```
 * query<String>(Regex("/(?<name>.+)/hello")) {
 *     val name = call.parameters["name"]
 *     ...
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.query)
 */
@JvmName("queryTypedPath")
public inline fun <reified R : Any> Route.query(
    path: Regex,
    crossinline body: suspend RoutingContext.(R) -> Unit
): Route = query(path) {
    body(call.receive())
}

private fun Route.createRouteFromRegexPath(regex: Regex): Route {
    return createChild(PathSegmentRegexRouteSelector(regex))
}

/**
 * A route selector that matches a segment of the path against a specified regular expression [regex].
 * This selector allows flexible matching of URI path segments by using regex patterns.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.routing.PathSegmentRegexRouteSelector)
 *
 * @property regex regular expression for matching path segments
 */
public class PathSegmentRegexRouteSelector(public val regex: Regex) : RouteSelector(), RoutePathComponent {

    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val prefix = if (regex.pattern.startsWith('/') || regex.pattern.startsWith("""\/""")) "/" else ""
        val postfix = if (regex.pattern.endsWith('/') && context.call.ignoreTrailingSlash) "/" else ""
        val pathSegments = context.segments.drop(segmentIndex).joinToString("/", prefix, postfix)
        val result = regex.find(pathSegments) ?: return RouteSelectorEvaluation.Failed

        val segmentIncrement = result.value.length.let { consumedLength ->
            if (pathSegments.length == consumedLength) {
                context.segments.size - segmentIndex
            } else if (pathSegments[consumedLength] == '/') {
                countSegments(result, consumedLength, prefix)
            } else if (consumedLength >= 1 && pathSegments[consumedLength - 1] == '/') {
                countSegments(result, consumedLength - 1, prefix)
            } else {
                return RouteSelectorEvaluation.Failed
            }
        }

        val groups = result.groups as MatchNamedGroupCollection
        val parameters = Parameters.build {
            GROUP_NAME_MATCHER.findAll(regex.pattern).forEach { matchResult ->
                val (_, name) = matchResult.destructured
                val value = groups[name]?.value ?: ""
                append(name, value)
            }
        }
        return RouteSelectorEvaluation.Success(
            quality = RouteSelectorEvaluation.qualityQueryParameter,
            parameters = parameters,
            segmentIncrement = segmentIncrement
        )
    }

    private fun countSegments(result: MatchResult, lastSlashPosition: Int, prefix: String): Int {
        val segments = result.value.substring(0, lastSlashPosition)
        val count = segments.count { it == '/' }
        return if (prefix == "/") count else count + 1
    }

    override fun toString(): String = "Regex(${regex.pattern})"

    public companion object {
        // JS doesn't support `Alpha`/`Alnum`
        // Wasm/native doesn't support `L`/`N`
        // the only difference between regexes is in it
        private val GROUP_NAME_MATCHER = when {
            PlatformUtils.IS_JS -> Regex("""(^|[^\\])\(\?<(\p{L}[\p{L}\p{N}]*)>(.*?[^\\])?\)""")
            else -> Regex("""(^|[^\\])\(\?<(\p{Alpha}\p{Alnum}*)>(.*?[^\\])?\)""")
        }
    }
}
