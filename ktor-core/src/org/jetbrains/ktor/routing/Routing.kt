package org.jetbrains.ktor.routing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*


class Routing(val application: Application) : RoutingEntry(parent = null, selector = Routing.RootRoutingSelector) {

    object RootRoutingSelector : RoutingSelector {
        override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation = throw UnsupportedOperationException()
        override fun toString(): String = ""
    }

    internal fun interceptor(call: ApplicationCall, next: ApplicationCall.() -> ApplicationCallResult): ApplicationCallResult {
        val resolveContext = RoutingResolveContext(this, call.request.requestLine, call.request.parameters, call.request.headers)
        val resolveResult = resolveContext.resolve()
        return when {
            resolveResult.succeeded -> {
                val routingCall = RoutingApplicationCall(call, resolveResult.entry, resolveResult.values)
                routingCall.executeEntry(resolveResult.entry)
            }
            else -> call.next()
        }
    }

    private fun RoutingApplicationCall.executeHandlers(handlers: List<RoutingApplicationCall.() -> ApplicationCallResult>): ApplicationCallResult {
        // Handlers are executed in the installation order, first one that handles a call wins
        for (handler in handlers) {
            val handlerResult = handler()
            if (handlerResult != ApplicationCallResult.Unhandled)
                return handlerResult
        }
        return ApplicationCallResult.Unhandled
    }

    private fun RoutingApplicationCall.executeEntry(entry: RoutingEntry): ApplicationCallResult {
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
        if (interceptors == null || interceptors.isEmpty()) {
            return executeHandlers(entry.handlers)
        }

        fun handle(index: Int, interceptors: List<RoutingInterceptor>): ApplicationCallResult = when {
            index < interceptors.size -> interceptors[index].function(this) { request -> handle(index + 1, interceptors) }
            else -> executeHandlers(entry.handlers)
        }

        return handle(0, interceptors)
    }

    companion object RoutingFeature : ApplicationFeature<Routing> {
        override val key: AttributeKey<Routing> = AttributeKey("Routing")
        override val name: String = "Routing"

        override fun install(application: Application, configure: Routing.() -> Unit) = Routing(application).apply {
            configure()
            application.intercept { next -> this@apply.interceptor(this, next) }
        }
    }
}

val RoutingEntry.application: Application get() = when {
    this is Routing -> application
    else -> parent?.application ?: throw UnsupportedOperationException("Cannot retrieve application from unattached routing entry")
}

public fun Application.routing(configure: Routing.() -> Unit) = install(Routing, configure)

