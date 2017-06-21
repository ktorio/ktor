package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import java.util.*

open class Route(val parent: Route?, val selector: RouteSelector) : ApplicationCallPipeline() {
    val children: MutableList<Route> = ArrayList()

    @Volatile var cachedPipeline: ApplicationCallPipeline? = null

    internal val handlers = ArrayList<PipelineInterceptor<Unit>>()

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

    fun handle(handler: PipelineInterceptor<Unit>) {
        handlers.add(handler)

        // Adding a handler invalidates only pipeline for this entry
        cachedPipeline = null
    }

    override fun intercept(phase: PipelinePhase, block: PipelineInterceptor<Unit>) {
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
            val routePipelines = mutableListOf<ApplicationCallPipeline>()
            while (current != null) {
                routePipelines.add(current)
                current = current.parent
            }

            for (index in routePipelines.lastIndex downTo 0) {
                val routePipeline = routePipelines[index]
                pipeline.phases.merge(routePipeline.phases)
                pipeline.receivePipeline.phases.merge(routePipeline.receivePipeline.phases)
                pipeline.sendPipeline.phases.merge(routePipeline.sendPipeline.phases)
            }

            val handlers = handlers
            for (index in 0..handlers.lastIndex) {
                pipeline.intercept(ApplicationCallPipeline.Call, handlers[index])
            }
            cachedPipeline = pipeline
            pipeline
        }
    }

    override fun toString() = when {
        parent == null -> "/"
        parent.parent == null -> "/$selector"
        else -> "$parent/$selector"
    }
}