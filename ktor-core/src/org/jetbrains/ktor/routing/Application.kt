package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
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
    val table = Routing()
    table.body()
    interceptRoute(table)
}

fun Application.interceptRoute(routing: RoutingEntry) {
    handler.intercept { context, next ->
        val resolveContext = RoutingResolveContext(context.request.requestLine, context.request.parameters, context.request.headers)
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
                processChain(chain, RoutingApplicationRequestContext(context, resolveResult))
            }
            else -> next(context)
        }
    }
}

fun RoutingEntry.methodAndPath(method: HttpMethod, path: String, body: RoutingEntry.() -> Unit) {
    method(method) {
        path(path) {
            body()
        }
    }
}

fun RoutingEntry.contentType(contentType: ContentType, build: RoutingEntry.() -> Unit) {
    header("Accept", "${contentType.contentType}/${contentType.contentSubtype}", build)
}


public fun RoutingEntry.get(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndPath(HttpMethod.Get, path, body)
public fun RoutingEntry.put(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndPath(HttpMethod.Put, path, body)
public fun RoutingEntry.delete(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndPath(HttpMethod.Delete, path, body)
public fun RoutingEntry.post(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndPath(HttpMethod.Post, path, body)
public fun RoutingEntry.options(path: String, body: RoutingEntry.() -> Unit): Unit = methodAndPath(HttpMethod.Options, path, body)

public fun RoutingEntry.get(body: RoutingEntry.() -> Unit): Unit = method(HttpMethod.Get, body)
public fun RoutingEntry.put(body: RoutingEntry.() -> Unit): Unit = method(HttpMethod.Put, body)
public fun RoutingEntry.delete(body: RoutingEntry.() -> Unit): Unit = method(HttpMethod.Delete, body)
public fun RoutingEntry.post(body: RoutingEntry.() -> Unit): Unit = method(HttpMethod.Post, body)
public fun RoutingEntry.options(body: RoutingEntry.() -> Unit): Unit = method(HttpMethod.Options, body)

