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
    private val tags: MicrometerMetrics.TagBuilder.() -> Unit
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
     * default 50%, 90% , 95% and 99% percentiles are configured
     * @property tags is called to generate the tags for this call. By default, [TagBuilder.defaultTags] are generated
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

        internal var tags: TagBuilder.() -> Unit = {
            defaultTags()
        }

        fun tags(block: TagBuilder.() -> Unit) {
            tags = block
        }
    }

    private fun Timer.Sample.recordDuration(call: ApplicationCall, throwable: Throwable? = null) {
        stop(
            Timer.builder(requestTimerName)
                .customize(call, throwable)
                .register(registry)
        )
    }

    private fun Timer.Builder.customize(call: ApplicationCall, throwable: Throwable?): Timer.Builder =
        apply { TagBuilder(this, call, throwable).tags() }


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
                configuration.tags
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

    private data class CallMeasure(val timer: Timer.Sample, var route: String? = null)
    /**
     * Encapsulates the [Timer.Builder] to allow adding or changing individual tags per call. The methods to set a
     * list of tags like [Timer.Builder.tags] are not available as it would be an easy error source for removing the
     * default tags ('route', 'method' etc) but.
     */
    class TagBuilder(
        private val builder: Timer.Builder,
        private val call: ApplicationCall,
        private val throwable: Throwable?
    ) {

        /**
         * @see [Timer.Builder.tag]
         */
        fun tag(name: String, value: String) {
            builder.tag(name, value)
        }

        /**
         * adds tags for [localAddress], [status], [route] and [method]
         */
        fun defaultTags() {
            localAddress()
            status()
            route()
            method()
        }

        /**
         * adds a tag for the local address in the format <host>:<port>
         */
        fun localAddress() = tag("local", call.request.local.let { "${it.host}:${it.port}" })

        /**
         * adds a tag for the http method (e.g "GET", "POST")
         */
        fun method() = tag("method", call.request.httpMethod.value)

        /**
         * adds a tag for the http route (e.g. "/somePath/{someParameter}")
         */
        fun route() = tag("route", call.attributes[measureKey].route ?: call.request.path())

        /**
         * adds a tag for the responded HTTP status or the throwable thrown during processing of the request
         * e.g. "200", "401", "java.lang.IllegalStateException"
         */
        fun status() = tag("status", getStatus())


        private fun getStatus(): String {
            return if (throwable != null) {
                throwable::class.qualifiedName ?: "anonymous exception"
            } else {
                call.response.status()?.value?.toString() ?: throw IllegalStateException(
                    "Sorry, this should not happen, we should have either a  http status code or a throwable"
                )
            }
        }
    }
}


