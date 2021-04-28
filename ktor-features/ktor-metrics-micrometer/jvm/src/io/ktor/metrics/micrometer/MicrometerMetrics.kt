/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.Tag.*
import io.micrometer.core.instrument.binder.*
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import io.micrometer.core.instrument.config.*
import io.micrometer.core.instrument.distribution.*
import java.util.concurrent.atomic.*

/**
 * Enables Micrometer support when installed. Exposes the following metrics:
 * <ul>
 *     <li><code>ktor.http.server.requests.active</code>: Gauge - The amount of active ktor requests</li>
 *     <li><code>ktor.http.server.requests</code>: Timer - Timer for all requests. By default no percentiles or
 *       histogram is exposed. Use the [Configuration.distributionStatisticConfig] to enable these.
 *       Tags by default (use [Configuration.tags] to configure the tags or add custom tags):
 *       <ul>
 *           <li><code>address</code>: The host and port of the request uri (e.g. 'www.ktor.io:443' from the uri
 *           'https://www.ktor.io/foo/bar' )</li>
 *           <li><code>method</code>: The http method (e.g. 'GET')</li>
 *           <li><code>route</code>: The use ktor route used for this request. (e.g. '/some/path/{someParameter}')
 *           <li><code>status</code>: The http status code that was set in the response) (or 404 if no handler was
 *           found for this request or 500 if an exception was thrown</li>
 *           <li><code>throwable</code>: The class name of the throwable that was eventually thrown while processing
 *           the request (or 'n/a' if no throwable had been thrown). Please note, that if an exception is thrown after
 *           calling [io.ktor.response.ApplicationResponseFunctionsKt.respond(io.ktor.application.ApplicationCall, java.lang.Object, kotlin.coroutines.Continuation<? super kotlin.Unit>)]
 *           , the tag is still "n/a"</li>
 *        <ul>
 *     <li>
 *  <ul>
 */
public class MicrometerMetrics private constructor(
    private val registry: MeterRegistry,
    timerDistributionConfig: DistributionStatisticConfig,
    private val distinctNotRegisteredRoutes: Boolean,
    private val timerBuilder: Timer.Builder.(call: ApplicationCall, throwable: Throwable?) -> Unit
) {

    @Deprecated(
        "This is going to become internal. " +
            "Please file a ticket and clarify, why do you need it."
    )
    public constructor(
        registry: MeterRegistry,
        timerDistributionConfig: DistributionStatisticConfig,
        timerBuilder: Timer.Builder.(call: ApplicationCall, throwable: Throwable?) -> Unit
    ) : this(registry, timerDistributionConfig, true, timerBuilder)

    private val active = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

    init {
        enableTimerDistributionConfig(timerDistributionConfig)
    }

    private fun enableTimerDistributionConfig(timerDistributionConfig: DistributionStatisticConfig) {
        registry.config().meterFilter(
            object : MeterFilter {
                override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig =
                    if (id.name == requestTimeTimerName) timerDistributionConfig.merge(config) else config
            }
        )
    }

    /**
     * Configures this Feature
     * @property baseName The base prefix for metrics. Default: [Feature.defaultBaseName]
     * @property registry The meter registry where the meters are registered. Mandatory
     * @property meterBinders The binders that are automatically bound to the registry. Default: [ClassLoaderMetrics],
     * [JvmMemoryMetrics], [ProcessorMetrics], [JvmGcMetrics], [ProcessorMetrics], [JvmThreadMetrics], [FileDescriptorMetrics]
     * @property distributionStatisticConfig configures the histogram and/or percentiles for all request timers. By
     * default 50%, 90% , 95% and 99% percentiles are configured. If your backend supports server side histograms you
     * should enable these instead with [DistributionStatisticConfig.Builder.percentilesHistogram] as client side
     * percentiles cannot be aggregated.
     * @property timers can be used to configure each timer to add custom tags or configure individual SLAs etc
     * @property distinctNotRegisteredRoutes specifies if requests for non existent routes should
     * contain request path or fallback to common `n/a` value. `true` by default
     * */
    public class Configuration {

        public var baseName: String = Feature.defaultBaseName

        public lateinit var registry: MeterRegistry

        public var distinctNotRegisteredRoutes: Boolean = true

        internal fun isRegistryInitialized() = this::registry.isInitialized

        public var meterBinders: List<MeterBinder> = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics()
        )

        public var distributionStatisticConfig: DistributionStatisticConfig =
            DistributionStatisticConfig.Builder()
                .percentiles(0.5, 0.9, 0.95, 0.99)
                .build()

        internal var timerBuilder: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> }

        /**
         * Configure micrometer timers
         */
        public fun timers(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
            timerBuilder = block
        }
    }

    private fun CallMeasure.recordDuration(call: ApplicationCall) {
        timer.stop(
            Timer.builder(requestTimeTimerName)
                .addDefaultTags(call, throwable)
                .customize(call, throwable)
                .register(registry)
        )
    }

    private fun Timer.Builder.customize(call: ApplicationCall, throwable: Throwable?) =
        this.apply { timerBuilder(call, throwable) }

    private fun Timer.Builder.addDefaultTags(call: ApplicationCall, throwable: Throwable?): Timer.Builder {
        val route = call.attributes[measureKey].route ?: if (distinctNotRegisteredRoutes) call.request.path() else "n/a"
        tags(
            listOf(
                of("address", call.request.local.let { "${it.host}:${it.port}" }),
                of("method", call.request.httpMethod.value),
                of("route", route),
                of("status", call.response.status()?.value?.toString() ?: "n/a"),
                of("throwable", throwable?.let { it::class.qualifiedName } ?: "n/a")
            )
        )
        return this
    }

    private fun before(call: ApplicationCall) {
        active?.incrementAndGet()

        call.attributes.put(measureKey, CallMeasure(Timer.start(registry)))
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
     * Micrometer feature installation object
     */
    public companion object Feature : ApplicationFeature<Application, Configuration, MicrometerMetrics> {
        private const val defaultBaseName: String = "ktor.http.server"

        private lateinit var baseName: String

        /**
         * Request time timer name
         */
        @Deprecated(
            "static request time timer name is deprecated",
            ReplaceWith("requestTimeTimerName"),
            DeprecationLevel.WARNING
        )
        public const val requestTimerName: String = "$defaultBaseName.requests"

        /**
         * Request time timer name with configurable base name
         */
        public val requestTimeTimerName: String
            get() = "$baseName.requests"

        /**
         * Active requests gauge name
         */
        @Deprecated(
            "static gauge name is deprecated",
            ReplaceWith("activeRequestsGaugeName"),
            DeprecationLevel.WARNING
        )
        public const val activeGaugeName: String = "$defaultBaseName.requests.active"

        /**
         * Active requests gauge name with configurable base name
         */
        public val activeRequestsGaugeName: String
            get() = "$baseName.requests.active"

        private val measureKey = AttributeKey<CallMeasure>("metrics")

        override val key: AttributeKey<MicrometerMetrics> = AttributeKey("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): MicrometerMetrics {
            val configuration = Configuration().apply(configure)

            if (configuration.baseName.isBlank()) {
                throw IllegalArgumentException(
                    "Base name should be defined"
                )
            }

            baseName = configuration.baseName

            if (!configuration.isRegistryInitialized()) {
                throw IllegalArgumentException(
                    "Meter registry is missing. Please initialize the field 'registry'"
                )
            }

            val feature = MicrometerMetrics(
                configuration.registry,
                configuration.distributionStatisticConfig,
                configuration.distinctNotRegisteredRoutes,
                configuration.timerBuilder
            )

            configuration.meterBinders.forEach { it.bindTo(configuration.registry) }

            val phase = PipelinePhase("MicrometerMetrics")
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

            val postSendPhase = PipelinePhase("MicrometerMetricsPostSend")
            pipeline.sendPipeline.insertPhaseAfter(ApplicationSendPipeline.After, postSendPhase)
            pipeline.sendPipeline.intercept(ApplicationSendPipeline.After) {
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
