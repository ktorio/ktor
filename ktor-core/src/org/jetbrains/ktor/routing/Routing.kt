package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*


class Routing(val application: Application) : Route(parent = null, selector = Routing.RootRouteSelector) {

    object RootRouteSelector : RouteSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation = throw UnsupportedOperationException()
        override fun toString(): String = ""
    }

    internal fun interceptor(context: PipelineContext<ApplicationCall>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call, call.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            val routingCall = RoutingApplicationCall(call, resolveResult.entry, resolveResult.values)
            val pipeline = buildEntryPipeline(resolveResult.entry)
            context.call.execution.execute(routingCall, pipeline)
        }
    }

    private fun buildEntryPipeline(entry: Route): Pipeline<ApplicationCall> {
        var current: Route? = entry
        val pipeline = ApplicationCallPipeline()
        val pipelines = mutableListOf<ApplicationCallPipeline>()
        while (current != null) {
            pipelines.add(0, current)
            current = current.parent
        }
        pipelines.forEach { pipeline.merge(it) }
        entry.handlers.forEach { pipeline.intercept(ApplicationCallPipeline.Call, it) }
        return pipeline
    }

    companion object RoutingFeature : ApplicationFeature<Application, Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit) = Routing(pipeline).apply {
            configure()
            pipeline.intercept(ApplicationCallPipeline.Call) { call ->
                this@apply.interceptor(this)
            }
        }
    }
}

val Route.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

