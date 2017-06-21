package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class Routing(val application: Application) : Route(parent = null, selector = RootRouteSelector) {
    suspend private fun interceptor(context: PipelineContext<Unit>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call, call.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            executeResult(context, resolveResult.entry, resolveResult.values)
        }
    }

    suspend private fun executeResult(context: PipelineContext<Unit>, route: Route, parameters: ValuesMap) {
        val routingCallPipeline = route.buildPipeline()
        val receivePipeline = ApplicationReceivePipeline().apply {
            phases.merge(context.call.receivePipeline.phases)
            phases.merge(routingCallPipeline.receivePipeline.phases)
        }
        val responsePipeline = ApplicationSendPipeline().apply {
            phases.merge(context.call.sendPipeline.phases)
            phases.merge(routingCallPipeline.sendPipeline.phases)
        }
        val routingCall = RoutingApplicationCall(context.call, receivePipeline, responsePipeline, route, parameters)
        routingCallPipeline.execute(routingCall)
    }

    companion object Feature : ApplicationFeature<Application, Routing, Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit): Routing {
            val routing = Routing(pipeline).apply(configure)
            pipeline.intercept(ApplicationCallPipeline.Call) { routing.interceptor(this) }
            return routing
        }
    }

    private object RootRouteSelector : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
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

