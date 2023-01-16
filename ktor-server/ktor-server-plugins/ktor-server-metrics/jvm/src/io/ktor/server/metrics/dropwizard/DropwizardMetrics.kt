/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.metrics.dropwizard

import com.codahale.metrics.*
import com.codahale.metrics.MetricRegistry.*
import com.codahale.metrics.jvm.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.util.*
import java.util.concurrent.*

/**
 * A configuration for the [DropwizardMetrics] plugin.
 */
@KtorDsl
public class DropwizardMetricsConfig {
    /**
     * Specifies the base name (prefix) of Ktor metrics used for monitoring HTTP requests.
     * @see [DropwizardMetrics]
     */
    public var baseName: String = name("ktor.calls")

    /**
     * Specifies the meter registry for your monitoring system.
     * @see [DropwizardMetrics]
     */
    public var registry: MetricRegistry = MetricRegistry()

    /**
     * Allows you to configure a set of metrics for monitoring the JVM.
     * You can disable these metrics by setting this property to `false`.
     * @see [DropwizardMetrics]
     */
    public var registerJvmMetricSets: Boolean = true
}

/**
 * A plugin that lets you configure the `Metrics` library to get
 * useful information about the server and incoming requests.
 *
 * You can learn more from [Dropwizard metrics](https://ktor.io/docs/dropwizard-metrics.html).
 */
public val DropwizardMetrics: ApplicationPlugin<DropwizardMetricsConfig> =
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

        on(MonitoringEvent(Routing.RoutingCallStarted)) { call ->
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

        @OptIn(InternalAPI::class)
        on(Metrics) { call ->
            active.inc()
            call.attributes.put(measureKey, CallMeasure(duration.time()))
        }

        on(ResponseSent) { call ->
            callFinished(call)

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

private fun PluginBuilder<DropwizardMetricsConfig>.callFinished(call: ApplicationCall) {
    val routingMetrics = call.attributes.takeOrNull(routingMetricsKey)
    val name = routingMetrics?.name ?: call.request.routeName
    val status = call.response.status()?.value ?: 0
    val statusMeter =
        pluginConfig.registry.meter(name(pluginConfig.baseName, name, status.toString()))
    statusMeter.mark()
    routingMetrics?.context?.stop()
}

private val ApplicationRequest.routeName: String
    get() {
        val metricUri = uri.ifEmpty { "/" }.let { if (it.endsWith('/')) it else "$it/" }
        return "$metricUri(method:${httpMethod.value})"
    }
