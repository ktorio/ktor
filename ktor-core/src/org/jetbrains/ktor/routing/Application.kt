package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

open class RoutingApplicationCall(context: ApplicationCall, val resolveResult: RoutingResolveResult)
: ApplicationCall by context {
    val parameters = ValuesMap.build {
        appendAll(resolveResult.values)
    }
}

public fun Application.routing(body: RoutingEntry.() -> Unit) {
    Routing().apply(body).installInto(this)
}

fun RoutingEntry.contentType(contentType: ContentType, build: RoutingEntry.() -> Unit) {
    header("Accept", "${contentType.contentType}/${contentType.contentSubtype}", build)
}

fun RoutingEntry.get(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Get, path) { handle(body) }
}

fun RoutingEntry.post(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Post, path) { handle(body) }
}

fun RoutingEntry.header(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Header, path) { handle(body) }
}

fun RoutingEntry.put(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Put, path) { handle(body) }
}

fun RoutingEntry.delete(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Delete, path) { handle(body) }
}

fun RoutingEntry.options(path: String, body: RoutingApplicationCall.() -> ApplicationCallResult) {
    route(HttpMethod.Options, path) { handle(body) }
}
