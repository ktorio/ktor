package io.ktor.metrics.micrometer

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.micrometer.core.instrument.*
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
 *       histogram is exposed. Use the [Configuration.timerCustomizer] to enable these.
 *       Tags:
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
    private val tagCustomizer: MicrometerMetrics.TagBuilder.(ApplicationCall) -> Unit
) {

    private val active = registry.gauge("$baseName.requests.active", AtomicInteger(0))

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
     * default 50%, 90% , 95% and 99% percentiles are configured
     * @property tagBuilder is called to customize the tags of the timer for each call. by default this is a no-op
     */
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

        var tagCustomizer: TagBuilder.(ApplicationCall) -> Unit = {}
    }

    private fun ApplicationCall.getLocalAddress() = request.local.let { "${it.host}:${it.port}" }

    private fun ApplicationCall.getStatusCode() = response.status()?.value?.toString() ?: "Unknown"

    private fun ApplicationCall.tags(throwable: Throwable? = null) = listOf<Tag>(
        Tag.of("local", getLocalAddress()),
        Tag.of("method", request.httpMethod.value),
        Tag.of("route", attributes[measureKey].route ?: request.path()),
        Tag.of("status", throwable?.let { it::class.qualifiedName } ?: getStatusCode())
    )

    private fun Timer.Sample.recordDuration(call: ApplicationCall, throwable: Throwable? = null) {
        stop(
            Timer.builder(requestTimerName)
                .tags(call.tags(throwable))
                .customize(call)
                .register(registry)
        )
    }

    private fun Timer.Builder.customize(call: ApplicationCall): Timer.Builder =
        apply { TagBuilder(this).tagCustomizer(call) }


    private fun before(call: ApplicationCall) {
        active?.incrementAndGet()

        call.attributes.put(measureKey, CallMeasure(Timer.start(registry)))
    }

    private fun after(call: ApplicationCall) {
        active?.decrementAndGet()

        call.attributes.getOrNull(measureKey)?.apply {
            timer.recordDuration(call)
        }
    }

    private fun exception(call: ApplicationCall, e: Throwable) {
        call.attributes.getOrNull(measureKey)?.apply {
            timer.recordDuration(call, e)
        }
    }

    companion object Feature : ApplicationFeature<Application, Configuration, MicrometerMetrics> {
        private const val baseName: String = "ktor.http.server"
        const val requestTimerName = "$baseName.requests"

        private val measureKey = AttributeKey<CallMeasure>("metrics")

        override val key = AttributeKey<MicrometerMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): MicrometerMetrics {
            val configuration = Configuration().apply(configure)
            val feature = MicrometerMetrics(
                configuration.registry,
                configuration.distributionStatisticConfig,
                configuration.tagCustomizer
            )

            configuration.meterBinders.forEach { it.bindTo(configuration.registry) }

            val phase = PipelinePhase("Metrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)

            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                    feature.after(call)
                } catch (e: Exception) {
                    feature.exception(call, e)
                    throw e
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                call.attributes[measureKey].route = call.route.parent.toString()
            }

            return feature
        }
    }

    private data class CallMeasure(val timer: Timer.Sample, var route: String? = null)
    /**
     * Encapsulates the [Timer.Builder] to allow adding or changing individual tags per call. The methods to set a
     * list of tags like [Timer.Builder.tags] are not available as it would be an easy error source for removing the
     * default tags ('route', 'method' etc) but.
     */
    class TagBuilder(private val builder: Timer.Builder) {

        /**
         * @see [Timer.Builder.tag]
         */
        fun tag(name: String, value: String) =
            builder.tag(name, value)
    }
}


