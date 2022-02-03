/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import com.codahale.metrics.MetricRegistry.*
import com.codahale.metrics.jvm.*
import io.ktor.server.application.*
import io.ktor.util.*
import java.util.concurrent.*

/**
 * Metrics plugin configuration object that is used during plugin installation.
 */
@KtorDsl
public class DropwizardMetricsConfig {
    /**
     * Dropwizard metrics base name (prefix)
     */
    public var baseName: String = name("ktor.calls")

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
 * Dropwizard metrics support plugin. See https://ktor.io/servers/features/metrics.html for details.
 */
public val DropwizardMetrics: ApplicationPlugin<Application, DropwizardMetricsConfig, PluginInstance> =
    createApplicationPlugin("DropwizardMetrics", ::DropwizardMetricsConfig) {
        val duration = pluginConfig.registry.timer(name(pluginConfig.baseName, "duration"))
        val active = pluginConfig.registry.counter(name(pluginConfig.baseName, "active"))
        val exceptions = pluginConfig.registry.meter(name(pluginConfig.baseName, "exceptions"))
        val httpStatus = ConcurrentHashMap<Int, Meter>()

        if (pluginConfig.registerJvmMetricSets) {
            listOf<Pair<String, () -> Metric>>(
                "jvm.memory" to ::MemoryUsageGaugeSet,
                "jvm.garbage" to ::GarbageCollectorMetricSet,
                "jvm.threads" to ::ThreadStatesGaugeSet,
                "jvm.files" to ::FileDescriptorRatioGauge,
                "jvm.attributes" to ::JvmAttributeGaugeSet
            ).filter { (name, _) ->
                !pluginConfig.registry.names.any { existingName -> existingName.startsWith(name) }
            }.forEach { (name, metric) -> pluginConfig.registry.register(name, metric()) }
        }

        on(CallFailed) { _, _ ->
            exceptions.mark()
        }

        on(CallRouted) { call ->
            val name = call.route.toString()
            val meter = pluginConfig.registry.meter(name(pluginConfig.baseName, name, "meter"))
            val timer = pluginConfig.registry.timer(name(pluginConfig.baseName, name, "timer"))
            meter.mark()
            val context = timer.time()
            call.attributes.put(
                routingMetricsKey,
                RoutingMetrics(name, context)
            )
        }

        on(RoutedCallProcessed) { call ->
            val routingMetrics = call.attributes.take(routingMetricsKey)
            val status = call.response.status()?.value ?: 0
            val statusMeter =
                pluginConfig.registry.meter(name(pluginConfig.baseName, routingMetrics.name, status.toString()))
            statusMeter.mark()
            routingMetrics.context.stop()
        }

        on(BeforeCall) { call ->
            active.inc()
            call.attributes.put(measureKey, CallMeasure(duration.time()))
        }

        on(AfterCall) { call ->
            active.dec()
            val meter = httpStatus.computeIfAbsent(call.response.status()?.value ?: 0) {
                pluginConfig.registry.meter(name(pluginConfig.baseName, "status", it.toString()))
            }
            meter.mark()
            call.attributes.getOrNull(measureKey)?.apply {
                timer.stop()
            }
        }
    }
