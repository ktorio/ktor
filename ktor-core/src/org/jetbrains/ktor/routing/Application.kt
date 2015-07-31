package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import java.util.*

open class RoutingApplicationRequest(applicationRequest: ApplicationRequest,
                                     val resolveResult: RoutingResolveResult) : ApplicationRequest by applicationRequest {

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
    val table = Routing()
    table.body()
    interceptRoute(table)
}

fun Application.interceptRoute(routing: RoutingEntry) {
    intercept { request, proceed ->
        val resolveContext = RoutingResolveContext(request.path(), request.parameters)
        val resolveResult = routing.resolve(resolveContext)
        when {
            resolveResult.succeeded -> {
                val chain = arrayListOf<RoutingInterceptor>()
                for (entry in resolveResult.entries) {
                    val interceptors = entry.interceptors.filter { !it.leafOnly }
                    chain.addAll(interceptors)
                }
                val handlers = resolveResult.entry.interceptors.filter { it.leafOnly }
                chain.addAll(handlers)
                processChain(chain, RoutingApplicationRequest(request, resolveResult))
            }
            else -> proceed(request)
        }
    }
}

public fun RoutingEntry.respond(body: ApplicationResponse.() -> ApplicationRequestStatus) {
    handle {
        respond {
            body()
        }
    }
}

public fun RoutingEntry.get(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndLocation(HttpMethod.Get, path, body)
public fun RoutingEntry.put(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndLocation(HttpMethod.Put, path, body)
public fun RoutingEntry.delete(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndLocation(HttpMethod.Delete, path, body)
public fun RoutingEntry.post(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndLocation(HttpMethod.Post, path, body)

public fun RoutingEntry.get(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Get, body)
public fun RoutingEntry.put(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Put, body)
public fun RoutingEntry.delete(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Delete, body)
public fun RoutingEntry.post(body: RoutingEntry.() -> Unit): Unit = methodParam(HttpMethod.Post, body)

