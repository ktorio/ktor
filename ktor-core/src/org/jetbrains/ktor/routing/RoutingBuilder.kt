package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
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
 * Builds a route to match parameter with specified [name]
 */
fun Route.param(name: String, build: Route.() -> Unit): Route {
    val selector = ParameterRouteSelector(name)
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
fun Route.contentType(contentType: ContentType, build: Route.() -> Unit): Route {
    return header("Accept", "${contentType.contentType}/${contentType.contentSubtype}", build)
}

/**
 * Builds a route to match `GET` requests with specified [path]
 */
fun Route.get(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Get, path) { handle(body) }
}

/**
 * Builds a route to match `GET` requests
 */
fun Route.get(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Get) { handle(body) }
}

/**
 * Builds a route to match `POST` requests with specified [path]
 */
fun Route.post(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Post, path) { handle(body) }
}

/**
 * Builds a route to match `POST` requests
 */
fun Route.post(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Post) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests with specified [path]
 */
fun Route.head(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Head, path) { handle(body) }
}

/**
 * Builds a route to match `HEAD` requests
 */
fun Route.head(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Head) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests with specified [path]
 */
fun Route.put(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Put, path) { handle(body) }
}

/**
 * Builds a route to match `PUT` requests
 */
fun Route.put(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Put) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests with specified [path]
 */
fun Route.delete(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Delete, path) { handle(body) }
}

/**
 * Builds a route to match `DELETE` requests
 */
fun Route.delete(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Delete) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests with specified [path]
 */
fun Route.options(path: String, body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return route(HttpMethod.Options, path) { handle(body) }
}

/**
 * Builds a route to match `OPTIONS` requests
 */
fun Route.options(body: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit): Route {
    return method(HttpMethod.Options) { handle(body) }
}

/**
 * Create a routing entry for specified path
 */
fun Route.createRoute(path: String): Route {
    val parts = RoutingPath.parse(path).parts
    var current: Route = this
    for (part in parts) {
        val selector = when (part.kind) {
            RoutingPathSegmentKind.TailCard -> UriPartTailcardRouteSelector(part.value)
            RoutingPathSegmentKind.Parameter -> when {
                part.optional -> UriPartOptionalParameterRouteSelector(part.value)
                else -> UriPartParameterRouteSelector(part.value)
            }
            RoutingPathSegmentKind.Constant ->
                when {
                    part.optional -> UriPartWildcardRouteSelector
                    else -> UriPartConstantRouteSelector(part.value)
                }
        }
        // there may already be entry with same selector, so join them
        current = current.select(selector)
    }
    return current
}
