package io.ktor.metrics.micrometer

//import io.ktor.auth.AuthenticationRouteSelector
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

class MicrometerMetrics(
    private val registry: MeterRegistry,
    private val timerCustomizer: Timer.Builder.() -> Unit
) {

    private val active = registry.gauge("$baseName.requests.active", AtomicInteger(0))

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

        var timerCustomizer: Timer.Builder.() -> Unit = {}
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
        Tag.of("route", attributes[measureKey].route?: request.path()),
        Tag.of("status", throwable?.let { it::class.qualifiedName } ?: getStatusCode())
    )

    private fun Timer.Sample.recordDuration(call: ApplicationCall, throwable: Throwable? = null) {
        stop(
            Timer.builder(requestTimerName)
                .tags(call.tags(throwable))
                .customize()
                .register(registry)
        )
    }

    private fun Timer.Builder.customize(): Timer.Builder = apply(timerCustomizer)

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


