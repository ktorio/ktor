package ktor.routing

import java.util.*

data class RoutingNode(val selector: RoutingSelector, val entry: RoutingEntry)

data class RoutingInterceptor(val handler: (RoutingApplicationRequest, (RoutingApplicationRequest) -> Boolean) -> Boolean)

open class RoutingEntry() {
    val children = ArrayList<RoutingNode> ()
    val interceptors = ArrayList<RoutingInterceptor>()

    public fun add(selector: RoutingSelector, entry: RoutingEntry): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }?.entry
        if (existingEntry == null) {
            children.add(RoutingNode(selector, entry))
            return entry
        }
        return existingEntry
    }

    protected fun resolve(request: RoutingResolveContext, pathIndex: Int, current: RoutingResolveResult): RoutingResolveResult {
        var failEntry: RoutingEntry? = null
        for ((selector, entry) in children) {
            val result = selector.evaluate(request, pathIndex)
            if (result.succeeded) {
                for ((key, values) in result.values) {
                    current.values.getOrPut(key, { arrayListOf() }).addAll(values)
                }
                val subtreeResult = entry.resolve(request, pathIndex + result.incrementIndex, current)
                if (subtreeResult.succeeded) {
                    subtreeResult.entries.add(0, this)
                    return subtreeResult
                } else {
                    failEntry = subtreeResult.entry
                }
            }
        }

        when (pathIndex) {
            request.parts.size() -> return RoutingResolveResult(true, this, current.values, arrayListOf(this))
            else -> return RoutingResolveResult(false, failEntry ?: this)
        }
    }

    public fun resolve(request: RoutingResolveContext): RoutingResolveResult {
        return resolve(request, 0, RoutingResolveResult(false, this, HashMap<String, MutableList<String>>()))
    }

    public fun intercept(handler: (request: RoutingApplicationRequest, proceed: (RoutingApplicationRequest) -> Boolean) -> Boolean) {
        interceptors.add(RoutingInterceptor(handler))
    }
}

public fun processChain(interceptors: List<RoutingInterceptor>, request: RoutingApplicationRequest): Boolean {
    fun handle(index: Int, request: RoutingApplicationRequest): Boolean {
        return if (index < interceptors.size()) {
            interceptors[index].handler(request) { request -> handle(index + 1, request) }
        } else {
            false
        }
    }

    return handle(0, request)
}

class Routing : RoutingEntry()