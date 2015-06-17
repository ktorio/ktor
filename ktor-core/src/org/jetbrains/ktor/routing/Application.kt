package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.util.*

class RoutingApplicationRequest(applicationRequest: ApplicationRequest,
                                resolveResult: RoutingResolveResult) : ApplicationRequest by applicationRequest {

    override val parameters: Map<String, List<String>>

    init {
        val result = HashMap<String, MutableList<String>>()
        for ((key, values) in applicationRequest.parameters) {
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
            else -> next(request)
        }
    }
}

public fun RoutingEntry.response(body: ApplicationResponse.() -> Unit) {
    handle {
        response {
            body()
        }
    }
}

public fun RoutingEntry.get(path: String, body: RoutingApplicationRequest.() -> Unit): Unit = methodAndLocation(HttpMethod.Get, path, body)
public fun RoutingEntry.put(path: String, body: RoutingApplicationRequest.() -> Unit): Unit = methodAndLocation(HttpMethod.Put, path, body)
public fun RoutingEntry.delete(path: String, body: RoutingApplicationRequest.() -> Unit): Unit = methodAndLocation(HttpMethod.Delete, path, body)
public fun RoutingEntry.post(path: String, body: RoutingApplicationRequest.() -> Unit): Unit = methodAndLocation(HttpMethod.Post, path, body)

public fun RoutingEntry.get(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Get, body)
public fun RoutingEntry.put(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Put, body)
public fun RoutingEntry.delete(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Delete, body)
public fun RoutingEntry.post(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Post, body)

