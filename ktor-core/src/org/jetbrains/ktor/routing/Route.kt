package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

open class Route(val parent: Route?, val selector: RouteSelector) : ApplicationCallPipeline() {
    val children: MutableList<Route> = ArrayList()

    internal val handlers = ArrayList<PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit>()

    fun select(selector: RouteSelector): Route {
        val existingEntry = children.firstOrNull { it.selector.equals(selector) }
        if (existingEntry == null) {
            val entry = Route(this, selector)
            children.add(entry)
            return entry
        }
        return existingEntry
    }

    fun invoke(body: Route.() -> Unit) = apply(body)

    fun handle(handler: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        handlers.add(handler)
    }

    override fun toString(): String = if (parent != null) "${parent.toString()}/${selector.toString()}" else selector.toString()
}