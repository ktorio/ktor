package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*

fun RoutingEntry.location(path: String, build: RoutingEntry.() -> Unit) {
    val parts = pathToParts(path)
    var current: RoutingEntry = this;
    for ((index, part) in parts.withIndex()) {
        val entry = RoutingEntry()
        val selector = when {
            part == "*" -> UriPartWildcardRoutingSelector()
            part.startsWith("**") -> UriPartTailcardRoutingSelector(part.drop(2))
            part.startsWith(":?") -> UriPartOptionalParameterRoutingSelector(part.drop(2))
            part.startsWith(":") -> UriPartParameterRoutingSelector(part.drop(1))
            else -> UriPartConstantRoutingSelector(part)
        }
        // there may already be entry with same selector, so join them
        current = current.add(selector, entry)
    }
    current.build()
}

fun RoutingEntry.param(name: String, value: String, build: RoutingEntry.() -> Unit) {
    val selector = ConstantParameterRoutingSelector(name, value)
    val entry = RoutingEntry()
    add(selector, entry).build()
}

fun RoutingEntry.param(name: String, build: RoutingEntry.() -> Unit) {
    val selector = ParameterRoutingSelector(name)
    val entry = RoutingEntry()
    add(selector, entry).build()
}

fun RoutingEntry.methodAndLocation(method: String, path: String, body: RoutingEntry.() -> Unit) {
    methodParam(method) {
        location(path) {
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
    intercept { request, next -> request.handle() }
}

fun RoutingEntry.methodParam(method: String, build: RoutingEntry.() -> Unit) = param("@method", method, build)

