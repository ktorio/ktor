package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

open class RoutingEntry(val parent: RoutingEntry?, val selector: RoutingSelector) : InterceptApplicationCall {
    val children: MutableList<RoutingEntry> = ArrayList()

    internal val interceptors = mutableListOf<PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit>()

    internal val handlers = ArrayList<PipelineContext<ApplicationCall>.() -> Unit>()

    fun select(selector: RoutingSelector): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }
        if (existingEntry == null) {
            val entry = RoutingEntry(this, selector)
            children.add(entry)
            return entry
        }
        return existingEntry
    }

    override fun intercept(interceptor: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        interceptors.add(interceptor)
    }

    fun handle(handler: PipelineContext<ApplicationCall>.() -> Unit) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}