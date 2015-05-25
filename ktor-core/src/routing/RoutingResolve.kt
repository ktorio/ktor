package ktor.routing

import ktor.application.*
import java.util.*

class RoutingResolveContext(val uri: String, val parameters: Map<String, List<String>> = mapOf()) {
    val parts = pathToParts(uri)
}

data class RoutingResolveResult(val succeeded: Boolean,
                                val entry: RoutingEntry,
                                val values: MutableMap<String, MutableList<String>> = hashMapOf(),
                                val entries: MutableList<RoutingEntry> = arrayListOf())

class RoutingApplicationRequest(applicationRequest: ApplicationRequest,
                                resolveResult: RoutingResolveResult) : ApplicationRequest by applicationRequest {

    override val parameters: Map<String, List<String>>
    init {
        val result = HashMap<String, MutableList<String>>()
        for ((key, values) in applicationRequest.parameters) {
            result.getOrPut(key, { arrayListOf() }).addAll(values)
        }
        for ((key, values) in resolveResult.values) {
            if (!result.containsKey(key)) { // HACK: should think about strategy of merging params and resolution values
                result.getOrPut(key, { arrayListOf() }).addAll(values)
            }
        }
        parameters = result
    }
}

fun pathToParts(path: String) =
        if (path == "/")
            listOf("")
        else
            path.splitBy("/").filter { it.length() > 0 }.toList()

fun Application.routing(body: RoutingEntry.() -> Unit) {
    val table = RoutingEntry()
    table.body()
    interceptRoute(table)
}

fun Application.interceptRoute(routing: RoutingEntry) {
    intercept { request, next ->
        val resolveContext = RoutingResolveContext(request.path(), request.parameters)
        val resolveResult = routing.resolve(resolveContext)
        when {
            resolveResult.succeeded -> {
                val chain = arrayListOf<RoutingInterceptor>()
                for (entry in resolveResult.entries) {
                    chain.addAll(entry.interceptors)
                }
                processChain(chain, RoutingApplicationRequest(request, resolveResult))
            }
            else -> false
        }
    }
}
