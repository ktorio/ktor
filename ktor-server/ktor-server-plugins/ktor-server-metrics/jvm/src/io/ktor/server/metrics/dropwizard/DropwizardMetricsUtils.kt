/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import io.ktor.server.application.*
import io.ktor.util.*

internal class RoutingMetrics(val name: String, val context: Timer.Context)
internal val routingMetricsKey = AttributeKey<RoutingMetrics>("metrics")

internal data class CallMeasure constructor(val timer: Timer.Context)
internal val measureKey = AttributeKey<CallMeasure>("metrics")

internal object AfterCall : Hook<(ApplicationCall) -> Unit> {
    override fun install(pipeline: ApplicationCallPipeline, handler: (ApplicationCall) -> Unit) {
        pipeline.intercept(ApplicationCallPipeline.Monitoring) {
            try {
                proceed()
            } finally {
                handler(call)
            }
        }
    }
}
