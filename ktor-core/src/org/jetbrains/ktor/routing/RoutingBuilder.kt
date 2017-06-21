package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*

/**
 * Builds a route to match specified [path]
 */
fun Route.route(path: String, build: Route.() -> Unit) = createRoute(path).apply(build)

/**
 * Builds a route to match specified [method] and [path]
 */
fun Route.route(method: HttpMethod, path: String, build: Route.() -> Unit): Route {
    val selector = HttpMethodRouteSelector(method)
    return select(selector).route(path, build)
}

/**
 * Builds a route to match specified [method]
 */
fun Route.method(method: HttpMethod, body: Route.() -> Unit): Route {
    val selector = HttpMethodRouteSelector(method)
    return select(selector).apply(body)
}

/**
 * Builds a route to match parameter with specified [name] and [value]
 */
fun Route.param(name: String, value: String, build: Route.() -> Unit): Route {
    val selector = ConstantParameterRouteSelector(name, value)
    return select(selector).apply(build)
}

/**
 * Builds a route to match parameter with specified [name] and capture its value
 */
fun Route.param(name: String, build: Route.() -> Unit): Route {
    val selector = ParameterRouteSelector(name)
    return select(selector).apply(build)
}

/**
 * Builds a route to optionally capture parameter with specified [name], if it exists
 */
fun Route.optionalParam(name: String, build: Route.() -> Unit): Route {
    val selector = OptionalParameterRouteSelector(name)
    return select(selector).apply(build)
}

/**
 * Builds a route to match header with specified [name] and [value]
 */
fun Route.header(name: String, value: String, build: Route.() -> Unit): Route {
    val selector = HttpHeaderRouteSelector(name, value)
    return select(selector).apply(build)
}

/**
 * Builds a route to match requests with specified [contentType]
 */
fun Route.accept(contentType: ContentType, build: Route.() -> Unit): Route {
    val selector = HttpAcceptRouteSelector(contentType)
    return select(selector).apply(build)
}

@Deprecated("This function is checking Accept header, use 'accept' function instead.", ReplaceWith("this.accept(contentType, build)"), DeprecationLevel.ERROR)
fun Route.contentType(contentType: ContentType, build: Route.() -> Unit): Route {
    return header(HttpHeaders.Accept, "${contentType.contentType}/${contentType.contentSubtype}", build)
}

fun Route.requestContentType(contentType: ContentType, build: Route.() -> Unit): Route {
    return header(HttpHeaders.ContentType, "${contentType.contentType}/${contentType.contentSubtype}", build)
}

/**
 * Builds a route to match `GET` requests with specified [path]
 */
fun Route.get(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Get, path) { handle(body) }
}

/**
 * Builds a route to match `GET` requests
 */
fun Route.get(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with specified [path]
 */
fun Route.post(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Post, path) { handle(body) }
}

/**
 * Builds a route to match `POST` requests
 */
fun Route.post(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests with specified [path]
 */
fun Route.head(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Head, path) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests
 */
fun Route.head(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with specified [path]
 */
fun Route.put(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Put, path) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests
 */
fun Route.put(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests with specified [path]
 */
fun Route.delete(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Delete, path) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests
 */
fun Route.delete(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with specified [path]
 */
fun Route.options(path: String, body: PipelineInterceptor<Unit>): Route {
    return route(HttpMethod.Options, path) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests
 */
fun Route.options(body: PipelineInterceptor<Unit>): Route {
    return method(HttpMethod.Options) { handle(body) }
}

/**
 * Create a routing entry for specified path
 */
fun Route.createRoute(path: String): Route {
    val parts = RoutingPath.parse(path).parts
    var current: Route = this
    for ((value, kind) in parts) {
        val selector = when (kind) {
            RoutingPathSegmentKind.Parameter -> UriPartParameterBuilder.parse(value)
            RoutingPathSegmentKind.Constant -> UriPartConstantBuilder.parse(value)
        }
        // there may already be entry with same selector, so join them
        current = current.select(selector)
    }
    return current
}

object UriPartParameterBuilder {
    fun parse(value: String): RouteSelector {
        val prefixIndex = value.indexOf('{')
        val suffixIndex = value.lastIndexOf('}')

        val prefix = if (prefixIndex == 0) null else value.substring(0, prefixIndex)
        val suffix = if (suffixIndex == value.length - 1) null else value.substring(suffixIndex + 1)

        val signature = value.substring(prefixIndex + 1, suffixIndex)
        return when {
            signature.endsWith("?") -> UriPartOptionalParameterRouteSelector(signature.dropLast(1), prefix, suffix)
            signature.endsWith("...") -> UriPartTailcardRouteSelector(signature.dropLast(3))
            else -> UriPartParameterRouteSelector(signature, prefix, suffix)
        }
    }

    fun parseName(value: String): String {
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

object UriPartConstantBuilder {
    fun parse(value: String): RouteSelector = when (value) {
        "*" -> UriPartWildcardRouteSelector
        else -> UriPartConstantRouteSelector(value)
    }
}