package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

open class RoutingApplicationRequestContext(context: ApplicationRequestContext, val resolveResult: RoutingResolveResult)
: ApplicationRequestContext by context {
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

fun RoutingEntry.get(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Get, path) { handle(body) }
}

fun RoutingEntry.post(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Post, path) { handle(body) }
}

fun RoutingEntry.header(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Header, path) { handle(body) }
}

fun RoutingEntry.put(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Put, path) { handle(body) }
}

fun RoutingEntry.delete(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Delete, path) { handle(body) }
}

fun RoutingEntry.options(path: String, body: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
    route(HttpMethod.Options, path) { handle(body) }
}
