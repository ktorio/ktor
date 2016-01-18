package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import java.util.*

data class RoutingInterceptor(val function: (RoutingApplicationCall, (RoutingApplicationCall) -> ApplicationCallResult) -> ApplicationCallResult)

open class RoutingEntry(val parent: RoutingEntry?, val selector: RoutingSelector) : InterceptApplicationCall<RoutingApplicationCall> {

    val children = ArrayList<RoutingEntry> ()
    val interceptors = ArrayList<RoutingInterceptor>()
    val handlers = ArrayList<RoutingApplicationCall.() -> ApplicationCallResult>()

    public fun select(selector: RoutingSelector): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }
        if (existingEntry == null) {
            val entry = RoutingEntry(this, selector)
            children.add(entry)
            return entry
        }
        return existingEntry
    }

    override fun intercept(interceptor: RoutingApplicationCall.(RoutingApplicationCall.() -> ApplicationCallResult) -> ApplicationCallResult) {
        interceptors.add(RoutingInterceptor(interceptor))
    }

    public fun handle(handler: RoutingApplicationCall.() -> ApplicationCallResult) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}