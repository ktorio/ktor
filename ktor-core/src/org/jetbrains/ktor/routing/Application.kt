package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.locations.*
import java.util.*

open class RoutingApplicationRequestContext(context: ApplicationRequestContext, val resolveResult: RoutingResolveResult)
: ApplicationRequestContext by context {
    val parameters: Map<String, List<String>>

    init {
        val result = HashMap<String, MutableList<String>>()
        for ((key, values) in context.request.parameters) {
            result.getOrPut(key, { arrayListOf() }).addAll(values)
        }
        for ((key, values) in resolveResult.values) {
            if (!result.containsKey(key)) {
                // HACK: should think about strategy of merging params and resolution values
                result.getOrPut(key, { arrayListOf() }).addAll(values)
            }
        }
        parameters = result
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
