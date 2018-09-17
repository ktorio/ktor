package io.ktor.jrpc.extensions

import io.ktor.application.call
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.pipeline.ContextDsl
import io.ktor.request.httpMethod
import io.ktor.routing.Route
import io.ktor.routing.route
import io.ktor.jrpc.routers.JrpcRouter
import io.ktor.response.respond

/**
 * Builds a route to match specified path and handle with JSON-RPC handler
 * @param path is a path for JRPC handler
 * @param configuration is a JRPC Router configuration block
 */
@ContextDsl
fun Route.jrpc(path: String, configuration: JrpcRouter.() -> Unit): Route {
    val router = JrpcRouter().apply(configuration)
    return jrpc(path, router)
}

/**
 * Builds a route to match specified path and handle with JSON-RPC handler
 * @param path is a path for JRPC handler
 * @param router is a already configured JRPC Router
 */
@ContextDsl
fun Route.jrpc(path: String, router: JrpcRouter): Route {
    return route(path) {
        val allowedContentType = ContentType.Application.Json.toString()
        handle({
            if (context.request.httpMethod == HttpMethod.Post) {
                val contentType = context.request.headers[HttpHeaders.ContentType]
                if (contentType == allowedContentType) {
                    router.jrpcPostHandler(this)
                } else {
                    call.respond(HttpStatusCode.UnsupportedMediaType,
                            "Content-Type $contentType is not allowed on JSON-RPC handler. You must use Content-Type: $allowedContentType")
                }
            } else {
                call.respond(HttpStatusCode.MethodNotAllowed,
                        "Method ${call.request.httpMethod.value} is not allowed on JSON-RPC handler")
            }
        })
    }
}
