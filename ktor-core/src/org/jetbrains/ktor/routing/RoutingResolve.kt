package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*

class RoutingResolveContext(val verb: HttpRequestLine,
                            val parameters: ValuesMap = ValuesMap.Empty,
                            val headers: ValuesMap = ValuesMap.Empty) {
    val path = parse(verb.path())

    private fun parse(path: String): List<String> {
        if (path == "/") return listOf()
        return path.split("/").filter { it.length > 0 }
    }
}

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: ValuesMap,
                                val quality: Double)



