/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import com.codahale.metrics.jvm.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import java.util.concurrent.*

/**
 * Dropwizard metrics support plugin. See https://ktor.io/servers/features/metrics.html for details.
 * @property registry dropwizard metrics registry
 * @property baseName metrics base name (prefix)
 */
public class DropwizardMetrics(
    public val registry: MetricRegistry,
    public val baseName: String = MetricRegistry.name("ktor.calls")
) {
    private val duration = registry.timer(MetricRegistry.name(baseName, "duration"))
    private val active = registry.counter(MetricRegistry.name(baseName, "active"))
    private val exceptions = registry.meter(MetricRegistry.name(baseName, "exceptions"))
    private val httpStatus = ConcurrentHashMap<Int, Meter>()

    /**
     * Metrics plugin configuration object that is used during plugin installation.
     */
    public class Configuration {
        /**
         * Dropwizard metrics base name (prefix)
         */
        public var baseName: String = MetricRegistry.name("ktor.calls")

        /**
         * Dropwizard metric registry.
         */
        public var registry: MetricRegistry = MetricRegistry()

        /**
         * By default, this plugin will register `MetricSet`s from
         * [metrics-jvm](https://metrics.dropwizard.io/4.1.2/manual/jvm.html) in the configured [MetricRegistry].
         * Set this to false to not register them.
         */
        public var registerJvmMetricSets: Boolean = true
    }

    /**
     * Metrics plugin companion
     */
    public companion object Plugin : ApplicationPlugin<Application, Configuration, DropwizardMetrics> {
        override val key: AttributeKey<DropwizardMetrics> = AttributeKey("metrics")

        private class RoutingMetrics(val name: String, val context: Timer.Context)

        private val routingMetricsKey = AttributeKey<RoutingMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): DropwizardMetrics {
            val configuration = Configuration().apply(configure)
            val plugin = DropwizardMetrics(configuration.registry, configuration.baseName)

            if (configuration.registerJvmMetricSets) {
                listOf<Pair<String, () -> Metric>>(
                    "jvm.memory" to ::MemoryUsageGaugeSet,
                    "jvm.garbage" to ::GarbageCollectorMetricSet,
                    "jvm.threads" to ::ThreadStatesGaugeSet,
                    "jvm.files" to ::FileDescriptorRatioGauge,
                    "jvm.attributes" to ::JvmAttributeGaugeSet
                )
                    .filter { (name, _) ->
                        !configuration.registry.names.any { existingName -> existingName.startsWith(name) }
                    }
                    .forEach { (name, metric) -> configuration.registry.register(name, metric()) }
            }

            val phase = PipelinePhase("DropwizardMetrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)
            pipeline.intercept(phase) {
                plugin.before(call)
                try {
                    proceed()
                } catch (e: Exception) {
                    plugin.exception(call, e)
                    throw e
                } finally {
                    plugin.after(call)
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                val name = call.route.toString()
                val meter = plugin.registry.meter(MetricRegistry.name(name, "meter"))
                val timer = plugin.registry.timer(MetricRegistry.name(name, "timer"))
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
                val statusMeter = plugin.registry.meter(MetricRegistry.name(routingMetrics.name, status.toString()))
                statusMeter.mark()
                routingMetrics.context.stop()
            }

            return plugin
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
