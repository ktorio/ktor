package org.jetbrains.ktor.routing

import org.jetbrains.ktor.http.*

fun RoutingResolveContext(path: String, parameters: Map<String, List<String>> = mapOf()): RoutingResolveContext {
    return RoutingResolveContext(HttpVerb(HttpMethod.Get, path, "HTTP/1.1"), parameters)
}

class RoutingResolveContext(val verb: HttpVerb,
                            val parameters: Map<String, List<String>> = mapOf(),
                            val headers: Map<String, String> = mapOf()) {
    val parts = pathToParts(verb.path())
}

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: MutableMap<String, MutableList<String>> = hashMapOf(),
                                val entries: MutableList<RoutingEntry> = arrayListOf())


fun pathToParts(path: String) =
        if (path == "/")
            listOf("")
        else
            path.split("/").filter { it.length() > 0 }.toList()

