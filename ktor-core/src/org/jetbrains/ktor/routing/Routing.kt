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

    internal fun interceptor(call: ApplicationCall) {
        val resolveContext = RoutingResolveContext(this, call.request.requestLine, call.request.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        if (resolveResult.succeeded) {
            val routingCall = RoutingApplicationCall(call, resolveResult.entry, resolveResult.values)
            routingCall.executeEntry(resolveResult.entry)
        }
    }

    private fun RoutingApplicationCall.executeHandlers(handlers: List<RoutingApplicationCall.() -> Unit>) {
        // Handlers are executed in the installation order, first one that handles a call wins
        for (handler in handlers) {
            handler()
        }
    }

    private fun RoutingApplicationCall.executeEntry(entry: RoutingEntry): Unit {
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

        // No interceptors, just call handlers without polluting call stacks
        if (interceptors != null && interceptors.isNotEmpty()) {
            Pipeline<RoutingApplicationCall>().apply {
                for (interceptor in interceptors!!)
                    intercept(interceptor.function)

                intercept {
                    executeHandlers(entry.handlers)
                }
            }.execute(this)
        } else {
            executeHandlers(entry.handlers)
        }
    }

    companion object RoutingFeature : ApplicationFeature<Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")
        override val name: String = "Routing"

        override fun install(application: Application, configure: Routing.() -> Unit) = Routing(application).apply {
            configure()
            application.intercept { call -> this@apply.interceptor(call) }
        }
    }
}

val RoutingEntry.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

public fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

