package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*


class Routing(val application: Application) : RoutingEntry(parent = null, selector = Routing.RootRoutingSelector) {

    object RootRoutingSelector : RoutingSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation = throw UnsupportedOperationException()
        override fun toString(): String = ""
    }

    internal fun interceptor(context: PipelineContext<ApplicationCall>) {
        val call = context.call
        val resolveContext = RoutingResolveContext(this, call.request.requestLine, call.request.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            val routingCall = RoutingApplicationCall(call, resolveResult.entry, resolveResult.values)
            val pipeline = buildEntryPipeline(resolveResult.entry)
            val execution = pipeline.execute(routingCall)
            if (execution.state == PipelineExecution.State.Pause) {
                context.pipeline.pause()
                execution.blockBuilders.add {
                    context.pipeline.proceed()
                }
            }
        }
    }

    private fun executeHandlers(context: PipelineContext<RoutingApplicationCall>, handlers: List<PipelineContext<RoutingApplicationCall>.() -> Unit>) {
        // Handlers are executed in the installation order, first one that handles a call wins
        for (handler in handlers) {
            context.handler()
        }
    }

    private fun buildEntryPipeline(entry: RoutingEntry): Pipeline<RoutingApplicationCall> {
        // Interceptors are rarely installed into routing entries, so don't create list unless there are some
        var interceptors: MutableList<RoutingInterceptor>? = null
        var current: RoutingEntry? = entry
        while (current != null) {
            if (current.interceptors.isNotEmpty()) {
                if (interceptors == null)
                    interceptors = arrayListOf()
                interceptors.addAll(0, current.interceptors)
            }
            current = current.parent
        }

        val pipeline = Pipeline<RoutingApplicationCall>()
        if (interceptors != null && interceptors.isNotEmpty()) {
            for (interceptor in interceptors)
                pipeline.intercept(interceptor.function)
        }

        pipeline.intercept {
            executeHandlers(this, entry.handlers)
        }
        return pipeline
    }

    companion object RoutingFeature : ApplicationFeature<Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")
        override val name: String = "Routing"

        override fun install(application: Application, configure: Routing.() -> Unit) = Routing(application).apply {
            configure()
            application.intercept { call ->
                this@apply.interceptor(this)
            }
        }
    }
}

val RoutingEntry.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

public fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

