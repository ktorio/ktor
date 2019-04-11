package io.ktor.routing

import io.ktor.application.*
import io.ktor.util.pipeline.*
import java.util.*

/**
 * Describes a node in a routing tree
 *
 * @param parent is a parent node in the tree, or null for root node
 * @param selector is an instance of [RouteSelector] for this node
 */
@ContextDsl
open class Route(val parent: Route?, val selector: RouteSelector) : ApplicationCallPipeline() {

    /**
     * List of child routes for this node
     */
    val children: List<Route> get() = childList

    private val childList: MutableList<Route> = ArrayList()

    @Volatile private var cachedPipeline: ApplicationCallPipeline? = null

    internal val handlers = ArrayList<PipelineInterceptor<Unit, ApplicationCall>>()

    /**
     * Creates a child node in this node with a given [selector] or returns an existing one with the same selector
     */
    fun createChild(selector: RouteSelector): Route {
        val existingEntry = childList.firstOrNull { it.selector == selector }
        if (existingEntry == null) {
            val entry = Route(this, selector)
            childList.add(entry)
            return entry
        }
        return existingEntry
    }

    /**
     * Allows using route instance for building additional routes
     */
    operator fun invoke(body: Route.() -> Unit) = body()

    /**
     * Installs a handler into this route which will be called when the route is selected for a call
     */
    fun handle(handler: PipelineInterceptor<Unit, ApplicationCall>) {
        handlers.add(handler)

        // Adding a handler invalidates only pipeline for this entry
        cachedPipeline = null
    }

    override fun afterIntercepted() {
        // Adding an interceptor invalidates pipelines for all children
        // We don't need synchronisation here, because order of intercepting and acquiring pipeline is indeterminate
        // If some child already cached its pipeline, it's ok to execute with outdated pipeline
        invalidateCachesRecursively()
    }

    private fun invalidateCachesRecursively() {
        cachedPipeline = null
        childList.forEach { it.invalidateCachesRecursively() }
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
                pipeline.merge(routePipeline)
                pipeline.receivePipeline.merge(routePipeline.receivePipeline)
                pipeline.sendPipeline.merge(routePipeline.sendPipeline)
            }

            val handlers = handlers
            for (index in 0..handlers.lastIndex) {
                pipeline.intercept(ApplicationCallPipeline.Call, handlers[index])
            }
            cachedPipeline = pipeline
            pipeline
        }
    }

    override fun toString(): String = when {
        parent == null -> "/$selector"
        parent.parent == null -> parent.toString().let { parentText ->
            when {
                parentText.endsWith('/') -> "$parentText$selector"
                else -> "$parentText/$selector"
            }
        }
        else -> "$parent/$selector"
    }
}
