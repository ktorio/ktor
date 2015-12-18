package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import java.util.*

data class RoutingInterceptor(val function: (RoutingApplicationRequestContext, (RoutingApplicationRequestContext) -> ApplicationRequestStatus) -> ApplicationRequestStatus)

open class RoutingEntry(val parent: RoutingEntry?, val selector: RoutingSelector) {

    val children = ArrayList<RoutingEntry> ()
    val interceptors = ArrayList<RoutingInterceptor>()
    val handlers = ArrayList<RoutingApplicationRequestContext.() -> ApplicationRequestStatus>()

    public fun select(selector: RoutingSelector): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }
        if (existingEntry == null) {
            val entry = RoutingEntry(this, selector)
            children.add(entry)
            return entry
        }
        return existingEntry
    }

    public fun intercept(interceptor: RoutingApplicationRequestContext.(RoutingApplicationRequestContext.() -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        interceptors.add(RoutingInterceptor(interceptor))
    }

    public fun handle(handler: RoutingApplicationRequestContext.() -> ApplicationRequestStatus) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}