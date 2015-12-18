package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.util.*

class Routing() : RoutingEntry(parent = null, selector = Routing.RootRoutingSelector) {

    object RootRoutingSelector : RoutingSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation = throw UnsupportedOperationException()
        override fun toString(): String = ""
    }

    data class Key<T : Any>(val name: String)

    val services = hashMapOf<Key<*>, Any>()
    fun <T : Any> addService(key: Key<T>, service: T) {
        services.put(key, service)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getService(key: Key<T>): T {
        val service = services[key] ?: throw UnsupportedOperationException("Cannot find service for key $key")
        return service as T
    }

    fun installInto(application: Application) {
        application.intercept { next -> interceptor(next) }
    }

    protected fun resolve(entry: RoutingEntry, request: RoutingResolveContext, segmentIndex: Int): RoutingResolveResult {
        var failEntry: RoutingEntry? = null
        val results = ArrayList<RoutingResolveResult>()
        for (child in entry.children) {
            val result = child.selector.evaluate(request, segmentIndex)
            if (result.succeeded) {
                val subtreeResult = resolve(child, request, segmentIndex + result.segmentIncrement)
                if (subtreeResult.succeeded ) {
                    val combinedValues = when {
                        result.values.isEmpty() -> subtreeResult.values
                        subtreeResult.values.isEmpty() -> result.values
                        else -> ValuesMap.build {
                            appendAll(result.values)
                            appendAll(subtreeResult.values)
                        }
                    }
                    val combinedQuality = combineQuality(subtreeResult.quality, result.quality)
                    val resolveResult = RoutingResolveResult(true, subtreeResult.entry, combinedValues, combinedQuality)
                    results.add(resolveResult)
                } else if (failEntry == null) {
                    // save first entry that failed to match for better diagnostic
                    failEntry = subtreeResult.entry
                }
            }
        }

        val bestChild = results.maxBy { it.quality }
        if (bestChild == null) {
            // no child matched, match is either current entry if path is done, or failure
            if (segmentIndex == request.path.parts.size)
                return RoutingResolveResult(true, entry, ValuesMap.Empty, 1.0)
            return RoutingResolveResult(false, failEntry ?: entry, ValuesMap.Empty, 0.0)
        }
        return bestChild
    }

    private fun combineQuality(quality1: Double, quality2: Double): Double {
        return quality1 * quality2
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

fun <T : Any> RoutingEntry.getService(key: Routing.Key<T>): T {
    return if (this is Routing)
        getService(key)
    else
        if (parent == null)
            throw UnsupportedOperationException("Services cannot be obtained from dangling route entries")
        else
            parent.getService(key)
}