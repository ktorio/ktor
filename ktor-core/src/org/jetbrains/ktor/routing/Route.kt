package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

open class Route(val parent: Route?, val selector: RouteSelector) : ApplicationCallPipeline() {
    val children: MutableList<Route> = ArrayList()

    @Volatile var cachedPipeline : ApplicationCallPipeline? = null

    internal val handlers = ArrayList<PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit>()

    fun select(selector: RouteSelector): Route {
        val existingEntry = children.firstOrNull { it.selector == selector }
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

        // Adding a handler invalidates only pipeline for this entry
        cachedPipeline = null
    }

    override fun intercept(phase: PipelinePhase, block: PipelineContext<ApplicationCall>.(ApplicationCall) -> Unit) {
        super.intercept(phase, block)

        // Adding an interceptor invalidates pipelines for all children
        // We don't need synchronisation here, because order of intercepting and acquiring pipeline is indeterminate
        // If some child already cached its pipeline, it's ok to execute with outdated pipeline
        invalidateCachesRecursively()
    }

    private fun invalidateCachesRecursively() {
        cachedPipeline = null
        children.forEach { it.invalidateCachesRecursively() }
    }

    internal fun buildPipeline(): ApplicationCallPipeline {
        return cachedPipeline ?: run {
            var current: Route? = this
            val pipeline = ApplicationCallPipeline()
            val entryPipelines = mutableListOf<ApplicationCallPipeline>()
            while (current != null) {
                entryPipelines.add(current)
                current = current.parent
            }

            for (index in entryPipelines.lastIndex downTo 0) {
                pipeline.phases.merge(entryPipelines[index].phases)
            }

            val handlers = handlers
            for (index in 0..handlers.lastIndex) {
                pipeline.intercept(ApplicationCallPipeline.Call, handlers[index])
            }
            cachedPipeline = pipeline
            pipeline
        }
    }

    override fun toString() = if (parent != null) "$parent/$selector" else selector.toString()
}