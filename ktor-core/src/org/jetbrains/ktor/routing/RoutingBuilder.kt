package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

fun RoutingEntry.path(path: String, build: RoutingEntry.() -> Unit) = createRoutingEntry(this, path).build()

public fun createRoutingEntry(routingEntry: RoutingEntry, path: String): RoutingEntry {
    val parts = pathToParts(path)
    var current: RoutingEntry = routingEntry;
    for (part in parts) {
        val selector = when {
            part == "*" -> UriPartWildcardRoutingSelector()
            part.startsWith("**") -> UriPartTailcardRoutingSelector(part.drop(2))
            part.startsWith(":?") -> UriPartOptionalParameterRoutingSelector(part.drop(2))
            part.startsWith(":") -> UriPartParameterRoutingSelector(part.drop(1))
            else -> UriPartConstantRoutingSelector(part)
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
    param("ContentType", "${contentType.contentType}/${contentType.contentSubtype}", build)
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

fun RoutingEntry.methodParam(method: String, build: RoutingEntry.() -> Unit) = param("@method", method, build)

