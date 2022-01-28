/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*

/**
 * Hook that will be triggered when a call was routed and before any other handlers.
 */
internal object CallRouted : Hook<(RoutingApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (RoutingApplicationCall) -> Unit) {
        application.environment?.monitor?.subscribe(Routing.RoutingCallStarted) { call -> handler(call) }
    }
}

/**
 * Hook that will be triggered after a call was successfully routed and it's processing has completely finished.
 */
internal object RoutedCallProcessed : Hook<(ApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (ApplicationCall) -> Unit) {
        application.environment?.monitor?.subscribe(Routing.RoutingCallFinished) { call -> handler(call) }
    }
}

internal class RoutingMetrics(val name: String, val context: Timer.Context)
internal val routingMetricsKey = AttributeKey<RoutingMetrics>("metrics")

internal data class CallMeasure constructor(val timer: Timer.Context)
internal val measureKey = AttributeKey<CallMeasure>("metrics")

internal object BeforeCall : Hook<(ApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (ApplicationCall) -> Unit) {
        application.intercept(ApplicationCallPipeline.Monitoring) {
            handler(call)
        }
    }
}

internal object AfterCall : Hook<(ApplicationCall) -> Unit> {
    override fun install(application: ApplicationCallPipeline, handler: (ApplicationCall) -> Unit) {
        application.intercept(ApplicationCallPipeline.Monitoring) {
            try {
                proceed()
            } finally {
                handler(call)
            }
        }
    }
}
