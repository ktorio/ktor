package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.request.*
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
 *           <li><code>local</code>: The host and port of the request uri (e.g. 'www.ktor.io')</li>
 *           <li><code>method</code>: The http method (e.g. 'GET')</li>
 *           <li><code>route</code>: The use ktor route used for this request. (e.g. '/some/path/{someParameter}')
 *           <li><code>status</code>: The http status code or the Exception name thrown during the request (e.g. '200', 'java.lang.IllegalArgumentException')
 *        <ul>
 *     <li>
 *  <ul>
 */
class MicrometerMetrics(
    private val registry: MeterRegistry,
    timerDistributionConfig: DistributionStatisticConfig,
    private val timerBuilder: Timer.Builder.(call: ApplicationCall, throwable: Throwable?) -> Unit
) {

    private val active = registry.gauge(activeGaugeName, AtomicInteger(0))

    init {
        enableTimerDistributionConfig(timerDistributionConfig)
    }

    private fun enableTimerDistributionConfig(timerDistributionConfig: DistributionStatisticConfig) {
        registry.config().meterFilter(object : MeterFilter {
            override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig =
                if (id.name == requestTimerName)
                    timerDistributionConfig.merge(config)
                else
                    config
        })
    }

    /**
     * Configures this Feature
     * @property registry The meter registry where the meters are registered. Mandatory
     * @property meterBinders The binders that are automatically bound to the registry. Default: [ClassLoaderMetrics],
     * [JvmMemoryMetrics], [ProcessorMetrics], [JvmGcMetrics], [ProcessorMetrics], [JvmThreadMetrics], [FileDescriptorMetrics]
     * @property distributionStatisticConfig configures the histogram and/or percentiles for all request timers. By
     * default 50%, 90% , 95% and 99% percentiles are configured. If your backend supports server side histograms you
     * should enable these instead with [DistributionStatisticConfig.Builder.percentilesHistogram] as client side
     * percentiles cannot be aggregated.
     * @property timerBuilder can be used to configure each timer to add custom tags or configure individual SLAs etc
     * */
    class Configuration {

        lateinit var registry: MeterRegistry

        var meterBinders: List<MeterBinder> = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            FileDescriptorMetrics()
        )

        var distributionStatisticConfig: DistributionStatisticConfig =
            DistributionStatisticConfig.Builder()
                .percentiles(0.5, 0.9, 0.95, 0.99)
                .build()

        internal var timerBuilder: Timer.Builder.(ApplicationCall, Throwable?) -> Unit = { _, _ -> }

        fun timerBuilder(block: Timer.Builder.(ApplicationCall, Throwable?) -> Unit) {
            timerBuilder = block
        }
    }

    private fun Timer.Sample.recordDuration(call: ApplicationCall, throwable: Throwable? = null) {
        stop(
            Timer.builder(requestTimerName)
                .addDefaultTags(call, throwable)
                .customize(call, throwable)
                .register(registry)
        )
    }

    private fun Timer.Builder.customize(call: ApplicationCall, throwable: Throwable?) =
        this.apply { timerBuilder(call, throwable) }

    private fun Timer.Builder.addDefaultTags(call: ApplicationCall, throwable: Throwable?): Timer.Builder {
        tags(
            listOf(
                of("address", call.request.local.let { "${it.host}:${it.port}" }),
                of("httpMethod", call.request.httpMethod.value),
                of("route", call.attributes[MicrometerMetrics.measureKey].route ?: call.request.path()),
                of("status", getStatus(call, throwable))
            )
        )
        return this
    }

    private fun before(call: ApplicationCall) {
        active?.incrementAndGet()

        call.attributes.put(measureKey, CallMeasure(Timer.start(registry)))
    }

    private fun after(call: ApplicationCall, e: Throwable? = null) {
        active?.decrementAndGet()

        call.attributes.getOrNull(measureKey)?.apply {
            timer.recordDuration(call, e)
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, MicrometerMetrics> {
        private const val baseName: String = "ktor.http.server"
        const val requestTimerName = "$baseName.requests"
        const val activeGaugeName = "$baseName.requests.active"

        private val measureKey = AttributeKey<CallMeasure>("metrics")

        override val key = AttributeKey<MicrometerMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): MicrometerMetrics {
            val configuration = Configuration().apply(configure)
            val feature = MicrometerMetrics(
                configuration.registry,
                configuration.distributionStatisticConfig,
                configuration.timerBuilder
            )

            configuration.meterBinders.forEach { it.bindTo(configuration.registry) }

            val phase = PipelinePhase("Metrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)

            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                    feature.after(call)
                } catch (e: Throwable) {
                    feature.after(call, e)
                    throw e
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                call.attributes[measureKey].route = call.route.parent.toString()
            }

            return feature
        }
    }
}

private data class CallMeasure(val timer: Timer.Sample, var route: String? = null)


private fun getStatus(call: ApplicationCall, throwable: Throwable?): String {
    return if (throwable != null) {
        throwable::class.qualifiedName ?: "anonymous exception"
    } else {
        call.response.status()?.value?.toString() ?: throw IllegalStateException(
            "Sorry, this should not happen, we should have either a  http status code or a throwable"
        )
    }
}


