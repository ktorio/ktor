package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

open class RoutingEntry(val parent: RoutingEntry?, val selector: RoutingSelector) : Pipeline<ApplicationCall>() {
    val children: MutableList<RoutingEntry> = ArrayList()

    internal val handlers = ArrayList<PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit>()

    fun select(selector: RoutingSelector): RoutingEntry {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }
        if (existingEntry == null) {
            val entry = RoutingEntry(this, selector)
            children.add(entry)
            return entry
        }
        return existingEntry
    }

    fun handle(handler: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}