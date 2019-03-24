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
import java.util.concurrent.atomic.*


/**
 * Enables Micrometer support when installed. Exposes the following metrics:
 * <ul>
 *     <li><code>ktor.http.server.requests.active</code>: Gauge - The amount of active ktor requests</i>
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
    private val timerCustomizer: Timer.Builder.(ApplicationCall) -> Unit
) {

    private val active = registry.gauge("$baseName.requests.active", AtomicInteger(0))

    /**
     * Configures this Feature
     * @property registry The meter registry where the meters are registered
     * @property meterBinders The binders that are automatically bound to the registry
     * @property timerCustomizer is called to customize the the timer (e.g. to enable histogram or percentile exposure)
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

        var timerCustomizer: Timer.Builder.(ApplicationCall) -> Unit = {}
    }

    companion object Feature : ApplicationFeature<Application, Configuration, MicrometerMetrics> {
        private const val baseName: String = "ktor.http.server"
        const val requestTimerName = "$baseName.requests"

        override val key = AttributeKey<MicrometerMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): MicrometerMetrics {
            val configuration = Configuration().apply(configure)
            val feature = MicrometerMetrics(
                configuration.registry,
                configuration.timerCustomizer
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

                with(feature) {
                    call.attributes[measureKey].route = call.route.parent.toString()
                }
            }

            return feature
        }
    }


    private data class CallMeasure(val timer: Timer.Sample, var route: String? = null)

    private val measureKey = AttributeKey<CallMeasure>("metrics")

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

    private fun Timer.Builder.customize(call: ApplicationCall): Timer.Builder = apply(this.timerCustomizer(call))

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
}


