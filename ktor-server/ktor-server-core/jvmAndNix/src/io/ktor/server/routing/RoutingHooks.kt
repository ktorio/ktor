package io.ktor.server.routing

import io.ktor.server.application.*

/**
 * Hook that will be triggered when a call was routed and before any other handlers.
 */
public object CallRouted : Hook<(RoutingApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (RoutingApplicationCall) -> Unit) {
        application.environment?.monitor?.subscribe(Routing.RoutingCallStarted) { call -> handler(call) }
    }
}

/**
 * Hook that will be triggered after a call processing was completely finished (i.e. all handlers were
 * executed and response was sent)
 */
public object CallFinished : Hook<(ApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (ApplicationCall) -> Unit) {
        application.environment?.monitor?.subscribe(Routing.RoutingCallFinished)  { call -> handler(call) }
    }
}
