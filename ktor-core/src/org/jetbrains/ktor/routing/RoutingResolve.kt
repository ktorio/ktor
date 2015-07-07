package org.jetbrains.ktor.routing

class RoutingResolveContext(val uri: String, val parameters: Map<String, List<String>> = mapOf()) {
    val parts = pathToParts(uri)
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

