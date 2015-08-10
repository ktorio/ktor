package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

fun RoutingEntry.path(path: String, build: RoutingEntry.() -> Unit) = createRoutingEntry(this, path).build()

public fun createRoutingEntry(routingEntry: RoutingEntry, path: String): RoutingEntry {
    val parts = RoutingPath.parse(path).parts
    var current: RoutingEntry = routingEntry;
    for (part in parts) {
        val selector = when (part.kind) {
            RoutingPathPartKind.TailCard -> UriPartTailcardRoutingSelector(part.value)
            RoutingPathPartKind.Parameter -> when {
                part.optional -> UriPartOptionalParameterRoutingSelector(part.value)
                else -> UriPartParameterRoutingSelector(part.value)
            }
            RoutingPathPartKind.Constant ->
                when {
                    part.optional -> UriPartWildcardRoutingSelector()
                    else -> UriPartConstantRoutingSelector(part.value)
                }
        }
        // there may already be entry with same selector, so join them
        current = current.select(selector)
    }
    return current
}

fun RoutingEntry.param(name: String, value: String, build: RoutingEntry.() -> Unit) {
    val selector = ConstantParameterRoutingSelector(name, value)
    select(selector).build()
}

fun RoutingEntry.contentType(contentType: ContentType, build: RoutingEntry.() -> Unit) {
    header("ContentType", "${contentType.contentType}/${contentType.contentSubtype}", build)
}

fun RoutingEntry.param(name: String, build: RoutingEntry.() -> Unit) {
    val selector = ParameterRoutingSelector(name)
    select(selector).build()
}

fun RoutingEntry.methodAndLocation(method: String, path: String, body: RoutingEntry.() -> Unit) {
    methodParam(method) {
        path(path) {
            body()
        }
    }
}

fun RoutingEntry.method(method: String, body: RoutingEntry.() -> Unit) {
    methodParam(method) {
        body()
    }
}

fun RoutingEntry.handle(handle: RoutingApplicationRequest.() -> ApplicationRequestStatus) {
    addInterceptor(true, handle)
}

fun RoutingEntry.addInterceptor(leafOnly: Boolean, handle: RoutingApplicationRequest.() -> ApplicationRequestStatus) {
    intercept(leafOnly) { request, next ->
        val result = request.handle()
        if (result == ApplicationRequestStatus.Unhandled)
            next(request)
        else
            result
    }
}

fun RoutingEntry.methodParam(method: String, build: RoutingEntry.() -> Unit) {
    val selector = HttpMethodRoutingSelector(method)
    select(selector).build()
}

fun RoutingEntry.header(name: String, value: String, build: RoutingEntry.() -> Unit) {
    val selector = HttpHeaderRoutingSelector(name, value)
    select(selector).build()
}

