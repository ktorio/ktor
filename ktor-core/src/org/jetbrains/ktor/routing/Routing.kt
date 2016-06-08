package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*


class Routing(val application: Application) : Route(parent = null, selector = Routing.RootRouteSelector) {

    object RootRouteSelector : RouteSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation = throw UnsupportedOperationException()
        override fun toString(): String = ""
    }

    internal fun interceptor(context: PipelineContext<ApplicationCall>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call.request.requestLine, call.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            val routingCall = RoutingApplicationCall(call, resolveResult.entry, resolveResult.values)
            val pipeline = buildEntryPipeline(resolveResult.entry)
            context.call.fork(routingCall, pipeline)
        }
    }

    private fun buildEntryPipeline(entry: Route): Pipeline<ApplicationCall> {
        // Interceptors are rarely installed into routing entries, so don't create list unless there are some
        var current: Route? = entry
        val pipeline = ApplicationCallPipeline()
        while (current != null) {
            current.interceptors.forEach { pipeline.intercept(ApplicationCallPipeline.Infrastructure, it) }
            current = current.parent
        }

        entry.handlers.forEach { pipeline.intercept(ApplicationCallPipeline.Call, it) }
        return pipeline
    }

    companion object RoutingFeature : ApplicationFeature<Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")
        override val name: String = "Routing"

        override fun install(application: Application, configure: Routing.() -> Unit) = Routing(application).apply {
            configure()
            application.intercept(ApplicationCallPipeline.Call) { call ->
                this@apply.interceptor(this)
            }
        }
    }
}

val Route.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

public fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

