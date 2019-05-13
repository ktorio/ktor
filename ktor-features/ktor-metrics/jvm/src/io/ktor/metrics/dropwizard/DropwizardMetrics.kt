/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.dropwizard

import com.codahale.metrics.*
import com.codahale.metrics.jvm.*
import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*
import java.util.concurrent.*

/**
 * Dropwizard metrics support feature. See https://ktor.io/servers/features/metrics.html for details.
 * @property registry dropwizard metrics registry
 * @property baseName metrics base name (prefix)
 */
class DropwizardMetrics(val registry: MetricRegistry, val baseName: String = MetricRegistry.name("ktor.calls")) {
    private val duration = registry.timer(MetricRegistry.name(baseName, "duration"))
    private val active = registry.counter(MetricRegistry.name(baseName, "active"))
    private val exceptions = registry.meter(MetricRegistry.name(baseName, "exceptions"))
    private val httpStatus = ConcurrentHashMap<Int, Meter>()

    /**
     * Metrics feature configuration object that is used during feature installation.
     */
    class Configuration {
        /**
         * Dropwizard metrics base name (prefix)
         */
        var baseName: String = MetricRegistry.name("ktor.calls")

        /**
         * Dropwizard metric registry.
         */
        var registry: MetricRegistry = MetricRegistry()
    }

    /**
     * Metrics feature companion
     */
    companion object Feature : ApplicationFeature<Application, Configuration, DropwizardMetrics> {
        override val key = AttributeKey<DropwizardMetrics>("metrics")

        private class RoutingMetrics(val name: String, val context: Timer.Context)

        private val routingMetricsKey = AttributeKey<RoutingMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DropwizardMetrics {
            val configuration = Configuration().apply(configure)
            val feature = DropwizardMetrics(configuration.registry, configuration.baseName)

            configuration.registry.register("jvm.memory", MemoryUsageGaugeSet())
            configuration.registry.register("jvm.garbage", GarbageCollectorMetricSet())
            configuration.registry.register("jvm.threads", ThreadStatesGaugeSet())
            configuration.registry.register("jvm.files", FileDescriptorRatioGauge())
            configuration.registry.register("jvm.attributes", JvmAttributeGaugeSet())

            val phase = PipelinePhase("DropwizardMetrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)
            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                } catch (e: Exception) {
                    feature.exception(call, e)
                    throw e
                } finally {
                    feature.after(call)
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                val name = call.route.toString()
                val meter = feature.registry.meter(MetricRegistry.name(name, "meter"))
                val timer = feature.registry.timer(MetricRegistry.name(name, "timer"))
                meter.mark()
                val context = timer.time()
                call.attributes.put(
                    routingMetricsKey,
                    RoutingMetrics(name, context)
                )
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallFinished) { call ->
                val routingMetrics = call.attributes.take(routingMetricsKey)
                val status = call.response.status()?.value ?: 0
                val statusMeter = feature.registry.meter(MetricRegistry.name(routingMetrics.name, status.toString()))
                statusMeter.mark()
                routingMetrics.context.stop()
            }

            return feature
        }
    }


    private data class CallMeasure(val timer: Timer.Context)

    private val measureKey = AttributeKey<CallMeasure>("metrics")

    private fun before(call: ApplicationCall) {
        active.inc()
        call.attributes.put(measureKey, CallMeasure(duration.time()))
    }

    private fun after(call: ApplicationCall) {
        active.dec()
        val meter = httpStatus.computeIfAbsent(call.response.status()?.value ?: 0) {
            registry.meter(MetricRegistry.name(baseName, "status", it.toString()))
        }
        meter.mark()
        call.attributes.getOrNull(measureKey)?.apply {
            timer.stop()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun exception(call: ApplicationCall, e: Throwable) {
        exceptions.mark()
    }
}
