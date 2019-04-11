package io.ktor.routing

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*

/**
 * Root routing node for an [Application]
 * @param application is an instance of [Application] for this routing
 */
class Routing(val application: Application) :
    Route(parent = null, selector = RootRouteSelector(application.environment.rootPath)) {
    private val tracers = mutableListOf<(RoutingResolveTrace) -> Unit>()

    /**
     * Register a route resolution trace function.
     * See https://ktor.io/servers/features/routing.html#tracing for details
     */
    fun trace(block: (RoutingResolveTrace) -> Unit) {
        tracers.add(block)
    }

    private suspend fun interceptor(context: PipelineContext<Unit, ApplicationCall>) {
        val resolveContext = RoutingResolveContext(this, context.call, tracers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult is RoutingResolveResult.Success) {
            executeResult(context, resolveResult.route, resolveResult.parameters)
        }
    }

    private suspend fun executeResult(
        context: PipelineContext<Unit, ApplicationCall>,
        route: Route,
        parameters: Parameters
    ) {
        val routingCallPipeline = route.buildPipeline()
        val receivePipeline = merge(
            context.call.request.pipeline,
            routingCallPipeline.receivePipeline
        ) { ApplicationReceivePipeline() }

        val responsePipeline = merge(
            context.call.response.pipeline,
            routingCallPipeline.sendPipeline
        ) { ApplicationSendPipeline() }

        val routingCall = RoutingApplicationCall(context.call, route, receivePipeline, responsePipeline, parameters)
        application.environment.monitor.raise(RoutingCallStarted, routingCall)
        try {
            routingCallPipeline.execute(routingCall)
        } finally {
            application.environment.monitor.raise(RoutingCallFinished, routingCall)
        }
    }

    private inline fun <Subject : Any, Context : Any, P : Pipeline<Subject, Context>> merge(
        first: P,
        second: P,
        build: () -> P
    ): P {
        if (first.isEmpty) {
            return second
        }
        if (second.isEmpty) {
            return first
        }
        return build().apply {
            merge(first)
            merge(second)
        }
    }

    /**
     * Installable feature for [Routing]
     */
    @Suppress("PublicApiImplicitType")
    companion object Feature : ApplicationFeature<Application, Routing, Routing> {

        /**
         * Event definition for when a routing-based call processing starts
         */
        val RoutingCallStarted = EventDefinition<RoutingApplicationCall>()
        /**
         * Event definition for when a routing-based call processing finished
         */
        val RoutingCallFinished = EventDefinition<RoutingApplicationCall>()

        override val key: AttributeKey<Routing> = AttributeKey("Routing")

        override fun install(pipeline: Application, configure: Routing.() -> Unit): Routing {
            val routing = Routing(pipeline).apply(configure)
            pipeline.intercept(ApplicationCallPipeline.Call) { routing.interceptor(this) }
            return routing
        }
    }
}

/**
 * Gets an [Application] for this [Route] by scanning the hierarchy to the root
 */
val Route.application: Application
    get() = when {
        this is Routing -> application
        else -> parent?.application
            ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
    }

/**
 * Gets or installs a [Routing] feature for the this [Application] and runs a [configuration] script on it
 */
@ContextDsl
fun Application.routing(configuration: Routing.() -> Unit): Routing =
    featureOrNull(Routing)?.apply(configuration) ?: install(Routing, configuration)

