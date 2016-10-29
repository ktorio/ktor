package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

class Routing(val application: Application) : Route(parent = null, selector = RootRouteSelector) {
    private fun interceptor(context: PipelineContext<ApplicationCall>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call, call.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            executeResult(context, resolveResult.entry, resolveResult.values)
        }
    }

    private fun executeResult(context: PipelineContext<ApplicationCall>, route: Route, parameters: ValuesMap) {
        val routingCall = RoutingApplicationCall(context.call, route, parameters)
        val pipeline = route.buildPipeline()
        context.fork(routingCall, pipeline)
    }

    companion object Feature : ApplicationFeature<Application, Routing, Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit): Routing {
            val routing = Routing(pipeline).apply(configure)
            pipeline.intercept(ApplicationCallPipeline.Call) { routing.interceptor(this) }
            return routing
        }
    }

    private object RootRouteSelector : RouteSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
            throw UnsupportedOperationException("Root selector should not be evaluated")
        }

        override fun toString(): String = ""
    }
}

val Route.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

