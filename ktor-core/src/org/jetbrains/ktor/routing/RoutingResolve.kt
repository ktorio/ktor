package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

fun RoutingResolveContext(path: String, parameters: ValuesMap = ValuesMap.Empty): RoutingResolveContext {
    return RoutingResolveContext(HttpRequestLine(HttpMethod.Get, path, "HTTP/1.1"), parameters)
}

fun RoutingResolveContext(path: String, parameters: ValuesMap = ValuesMap.Empty, headers: ValuesMap = ValuesMap.Empty): RoutingResolveContext {
    return RoutingResolveContext(HttpRequestLine(HttpMethod.Get, path, "HTTP/1.1"), parameters, headers)
}

class RoutingResolveContext(val verb: HttpRequestLine,
                            val parameters: ValuesMap = ValuesMap.Empty,
                            val headers: ValuesMap = ValuesMap.Empty) {
    val path = RoutingPath.parse(verb.path())
}

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: ValuesMap,
                                val quality: Double)



