package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import java.util.*

internal data class RoutingInterceptor(val function: (RoutingApplicationCall, (RoutingApplicationCall) -> ApplicationCallResult) -> ApplicationCallResult)

open class RoutingEntry(val parent: RoutingEntry?, val selector: RoutingSelector) : InterceptApplicationCall<RoutingApplicationCall> {
    val children: MutableList<RoutingEntry> = ArrayList()

    internal val interceptors = ArrayList<RoutingInterceptor>()
    internal val handlers = ArrayList<RoutingApplicationCall.() -> ApplicationCallResult>()

    fun select(selector: RoutingSelector): RoutingEntry {
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

    fun handle(handler: RoutingApplicationCall.() -> ApplicationCallResult) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}