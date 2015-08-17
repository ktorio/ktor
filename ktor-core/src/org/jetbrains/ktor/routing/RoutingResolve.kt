package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*

fun RoutingResolveContext(path: String, parameters: Map<String, List<String>> = mapOf()): RoutingResolveContext {
    return RoutingResolveContext(HttpRequestLine(HttpMethod.Get, path, "HTTP/1.1"), parameters)
}

class RoutingResolveContext(val verb: HttpRequestLine,
                            val parameters: Map<String, List<String>> = mapOf(),
                            val headers: Map<String, String> = mapOf()) {
    val path = RoutingPath.parse(verb.path())
}

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: MutableMap<String, MutableList<String>> = hashMapOf())



