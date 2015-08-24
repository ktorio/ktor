package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import java.util.*

class Routing() : RoutingEntry(parent = null) {

    data class Key<T : Any>(val name: String)

    val services = hashMapOf<Key<*>, Any>()
    fun addService<T : Any>(key: Key<T>, service: T) {
        services.put(key, service)
    }

    fun getService<T : Any>(key: Key<T>): T {
        val service = services[key] ?: throw UnsupportedOperationException("Cannot find service for key $key")
        return service as T
    }

    fun installInto(application: Application) {
        application.intercept { next -> interceptor(next) }
    }

    protected fun resolve(entry: RoutingEntry, request: RoutingResolveContext, segmentIndex: Int): RoutingResolveResult {
        var failEntry: RoutingEntry? = null
        for ((selector, child) in entry.children) {
            val result = selector.evaluate(request, segmentIndex)
            if (result.succeeded) {
                val subtreeResult = resolve(child, request, segmentIndex + result.segmentIncrement)
                if (subtreeResult.succeeded) {
                    return RoutingResolveResult(true, subtreeResult.entry, ValuesMap.build {
                        appendAll(result.values)
                        appendAll(subtreeResult.values)
                    })
                } else {
                    failEntry = subtreeResult.entry
                }
            }
        }

        when (segmentIndex) {
            request.path.parts.size() -> return RoutingResolveResult(true, entry, ValuesMap.Empty)
            else -> return RoutingResolveResult(false, failEntry ?: entry, ValuesMap.Empty)
        }
    }

    public fun resolve(request: RoutingResolveContext): RoutingResolveResult {
        return resolve(this, request, 0)
    }

    private fun ApplicationRequestContext.interceptor(next: ApplicationRequestContext.() -> ApplicationRequestStatus): ApplicationRequestStatus {
        val resolveContext = RoutingResolveContext(request.requestLine, request.parameters, request.headers)
        val resolveResult = resolve(resolveContext)
        return when {
            resolveResult.succeeded -> {
                val chain = arrayListOf<RoutingInterceptor>()
                var current: RoutingEntry? = resolveResult.entry
                while (current != null) {
                    chain.addAll(0, current.interceptors)
                    current = current.parent
                }

                val handlers = resolveResult.entry.handlers
                val context = RoutingApplicationRequestContext(this, resolveResult)
                processChain(chain, context, handlers)
            }
            else -> next()
        }
    }

    private fun processChain(interceptors: List<RoutingInterceptor>, request: RoutingApplicationRequestContext, handlers: ArrayList<RoutingApplicationRequestContext.() -> ApplicationRequestStatus>): ApplicationRequestStatus {
        fun handle(index: Int, context: RoutingApplicationRequestContext): ApplicationRequestStatus {
            when (index) {
                in interceptors.indices -> {
                    return interceptors[index].function(context) { request -> handle(index + 1, request) }
                }
                else -> {
                    for (handler in handlers) {
                        val handlerResult = context.handler()
                        if (handlerResult != ApplicationRequestStatus.Unhandled)
                            return handlerResult
                    }
                    return ApplicationRequestStatus.Unhandled
                }
            }
        }

        return handle(0, request)
    }
}

fun RoutingEntry.getService<T : Any>(key: Routing.Key<T>): T {
    return if (this is Routing)
        getService(key)
    else
        if (parent == null)
            throw UnsupportedOperationException("Services cannot be obtained from dangling route entries")
        else
            parent.getService(key)
}