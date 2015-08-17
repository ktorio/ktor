package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import java.util.*

data class RoutingNode(val selector: RoutingSelector, val entry: RoutingEntry)

data class RoutingInterceptor(val function: (RoutingApplicationRequestContext, (RoutingApplicationRequestContext) -> ApplicationRequestStatus) -> ApplicationRequestStatus)

open class RoutingEntry(val parent: RoutingEntry?) {
    val children = ArrayList<RoutingNode> ()
    val interceptors = ArrayList<RoutingInterceptor>()
    val handlers = ArrayList<RoutingApplicationRequestContext.() -> ApplicationRequestStatus>()

    public fun select(selector: RoutingSelector): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }?.entry
        if (existingEntry == null) {
            val entry = createChild()
            children.add(RoutingNode(selector, entry))
            return entry
        }
        return existingEntry
    }

    protected fun resolve(request: RoutingResolveContext, segmentIndex: Int, current: RoutingResolveResult): RoutingResolveResult {
        var failEntry: RoutingEntry? = null
        for ((selector, entry) in children) {
            val result = selector.evaluate(request, segmentIndex)
            if (result.succeeded) {
                for ((key, values) in result.values) {
                    current.values.getOrPut(key, { arrayListOf() }).addAll(values)
                }
                val subtreeResult = entry.resolve(request, segmentIndex + result.segmentIncrement, current)
                if (subtreeResult.succeeded) {
                    return subtreeResult
                } else {
                    failEntry = subtreeResult.entry
                }
            }
        }

        when (segmentIndex) {
            request.path.parts.size() -> return RoutingResolveResult(true, this, current.values)
            else -> return RoutingResolveResult(false, failEntry ?: this)
        }
    }

    public fun resolve(request: RoutingResolveContext): RoutingResolveResult {
        return resolve(request, 0, RoutingResolveResult(false, this, HashMap<String, MutableList<String>>()))
    }

    public fun addInterceptor(interceptor: (RoutingApplicationRequestContext, (RoutingApplicationRequestContext) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        interceptors.add(RoutingInterceptor(interceptor))
    }

    public fun addHandler(handler: (RoutingApplicationRequestContext) -> ApplicationRequestStatus) {
        handlers.add(handler)
    }

    open fun createChild(): RoutingEntry = RoutingEntry(this)
}

class Routing : RoutingEntry(null) {
    data class Key<T : Any>(val name: String)

    val services = hashMapOf<Key<*>, Any>()
    fun addService<T : Any>(key: Key<T>, service: T) {
        services.put(key, service)
    }

    fun getService<T : Any>(key: Key<T>): T {
        val service = services[key] ?: throw UnsupportedOperationException("Cannot find service for key $key")
        return service as T
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