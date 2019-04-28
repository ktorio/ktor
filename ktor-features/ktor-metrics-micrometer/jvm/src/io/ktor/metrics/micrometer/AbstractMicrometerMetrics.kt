/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.response.ApplicationSendPipeline
import io.ktor.routing.Routing
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelinePhase
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import java.util.concurrent.atomic.AtomicInteger


/**
 * Abstract class for several micrometer features.
 */
abstract class AbstractMicrometerMetrics<TMeterRegistry : MeterRegistry,
    TConfiguration : AbstractMicrometerMetrics.AbstractConfiguration<TMeterRegistry>>(
    val config: TConfiguration
) {

    private val active = config.registry.gauge(activeGaugeName, AtomicInteger(0))

    init {
        enableTimerDistributionConfig(config.distributionStatisticConfig)
    }

    private fun enableTimerDistributionConfig(timerDistributionConfig: DistributionStatisticConfig) {
        config.registry.config().meterFilter(object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig =
                if (id.name == requestTimerName)
                    timerDistributionConfig.merge(config)
                else
                    config
        })
    }

    private fun CallMeasure.recordDuration(call: ApplicationCall) {
        timer.stop(
            Timer.builder(requestTimerName)
                .addDefaultTags(call, throwable)
                .customize(call, throwable)
                .register(config.registry)
        )
    }

    private fun Timer.Builder.customize(call: ApplicationCall, throwable: Throwable?) =
        this.apply {
            config.timerBuilder.invoke(this, call, throwable)
        }

    private fun Timer.Builder.addDefaultTags(call: ApplicationCall, throwable: Throwable?): Timer.Builder {
        tags(
            listOf(
                Tag.of("address", call.request.local.let { "${it.host}:${it.port}" }),
                Tag.of("method", call.request.httpMethod.value),
                Tag.of("route", call.attributes[measureKey].route ?: call.request.path()),
                Tag.of("status", call.response.status()?.value?.toString() ?: "n/a"),
                Tag.of("throwable", throwable?.let { it::class.qualifiedName } ?: "n/a")
            )
        )
        return this
    }

    private fun before(call: ApplicationCall) {
        active?.incrementAndGet()

        call.attributes.put(
            measureKey,
            CallMeasure(Timer.start(config.registry))
        )
    }

    private fun after(call: ApplicationCall) {
        active?.decrementAndGet()

        call.attributes.getOrNull(measureKey)?.recordDuration(call)

    }

    private fun throwable(call: ApplicationCall, t: Throwable) {
        call.attributes.getOrNull(measureKey)?.apply {
            throwable = t
        }
    }

    /**
     * Configures this MetricsFeature
     * @property registry The meter registry where the meters are registered. Mandatory
     * @property meterBinders The binders that are automatically bound to the registry. Default: [ClassLoaderMetrics],
     * [JvmMemoryMetrics], [ProcessorMetrics], [JvmGcMetrics], [ProcessorMetrics], [JvmThreadMetrics], [FileDescriptorMetrics]
     * @property distributionStatisticConfig configures the histogram and/or percentiles for all request timers. By
     * default 50%, 90% , 95% and 99% percentiles are configured. If your backend supports server side histograms you
     * should enable these instead with [DistributionStatisticConfig.Builder.percentilesHistogram] as client side
     * percentiles cannot be aggregated.
     * @property timers can be used to configure each timer to add custom tags or configure individual SLAs etc
     * */
    open class AbstractConfiguration<TMeterRegistry : MeterRegistry> {

        open lateinit var registry: TMeterRegistry

        var meterBinders: List<MeterBinder> = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics()
        )

        open var distributionStatisticConfig: DistributionStatisticConfig =
            DistributionStatisticConfig.Builder()
                .percentiles(0.5, 0.9, 0.95, 0.99)
                .build()

        internal var timerBuilder: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> }

        /**
         * Configure micrometer timers
         */
        fun timers(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
            timerBuilder = block
        }

        /**
         * Configure micrometer timers
         */
        @Deprecated("Use timers instead.", ReplaceWith("timers(block)"), level = DeprecationLevel.ERROR)
        fun timerBuilder(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
            timers(block)
        }

        internal fun isRegistryInitialized() =
            try {
                registry
                true
            } catch (e: UninitializedPropertyAccessException) {
                false
            }

    }


    companion object {

        private val measureKey = AttributeKey<CallMeasure>("metrics")

        private const val baseName: String = "ktor.http.server"

        /**
         * The name for the timers registered by this feature
         */
        const val requestTimerName = "$baseName.requests"

        /**
         * The name of the active gauge registered by this feature
         */
        const val activeGaugeName = "$baseName.requests.active"
    }

    abstract class AbstractMetricsFeature<TMeterRegistry : MeterRegistry,
        TConfiguration : AbstractConfiguration<TMeterRegistry>>(
        private val constructor: (TConfiguration) -> AbstractMicrometerMetrics<TMeterRegistry, TConfiguration>,
        private val configConstructor: () -> TConfiguration
    ) :
        ApplicationFeature<Application, TConfiguration, AbstractMicrometerMetrics<TMeterRegistry, TConfiguration>> {

        override val key: AttributeKey<AbstractMicrometerMetrics<TMeterRegistry, TConfiguration>> =
            AttributeKey("metrics")

        override fun install(pipeline: Application, configure: TConfiguration.() -> Unit):
            AbstractMicrometerMetrics<TMeterRegistry, TConfiguration> {
            val configuration = configConstructor().apply(configure)

            if (!configuration.isRegistryInitialized()) {
                throw IllegalArgumentException(
                    "Meter registry is missing. Please initialize the field 'registry'"
                )
            }

            val feature = constructor(configuration)

            configuration.meterBinders.forEach { it.bindTo(configuration.registry) }

            val phase = PipelinePhase(this::class.qualifiedName + "Metrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)

            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.throwable(call, e)
                    throw e
                }
            }

            val postSendPhase = PipelinePhase(this::class.qualifiedName + "MetricsPostSend")
            pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
            pipeline.sendPipeline.intercept(postSendPhase) {
                try {
                    proceed()
                } finally {
                    feature.after(call)
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                call.attributes[measureKey].route = call.route.parent.toString()
            }
            return feature
        }
    }
}


private data class CallMeasure(
    val timer: Timer.Sample,
    var route: String? = null,
    var throwable: Throwable? = null
)
