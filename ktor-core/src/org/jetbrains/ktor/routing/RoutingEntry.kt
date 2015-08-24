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
            val entry = RoutingEntry(this)
            children.add(RoutingNode(selector, entry))
            return entry
        }
        return existingEntry
    }

    public fun addInterceptor(interceptor: (RoutingApplicationRequestContext, (RoutingApplicationRequestContext) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        interceptors.add(RoutingInterceptor(interceptor))
    }

    public fun addHandler(handler: (RoutingApplicationRequestContext) -> ApplicationRequestStatus) {
        handlers.add(handler)
    }
}