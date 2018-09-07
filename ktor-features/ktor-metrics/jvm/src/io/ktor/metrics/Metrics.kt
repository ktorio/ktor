package io.ktor.metrics

import com.codahale.metrics.*
import com.codahale.metrics.jvm.*
import io.ktor.application.*
import io.ktor.util.pipeline.*
import io.ktor.routing.*
import io.ktor.util.*
import java.util.concurrent.*

class Metrics(val registry: MetricRegistry) {
    val baseName: String = MetricRegistry.name("ktor.calls")
    private val duration = registry.timer(MetricRegistry.name(baseName, "duration"))
    private val active = registry.counter(MetricRegistry.name(baseName, "active"))
    private val exceptions = registry.meter(MetricRegistry.name(baseName, "exceptions"))
    private val httpStatus = ConcurrentHashMap<Int, Meter>()

    class Configuration {
        val registry = MetricRegistry()
    }

    companion object Feature : ApplicationFeature<Application, Configuration, Metrics> {
        override val key = AttributeKey<Metrics>("metrics")

        private class RoutingMetrics(val name: String, val context: Timer.Context)

        private val routingMetricsKey = AttributeKey<RoutingMetrics>("metrics")

        override fun install(pipeline: Application, configure: Configuration.() -> Unit): Metrics {
            val configuration = Configuration().apply(configure)
            val feature = Metrics(configuration.registry)

            configuration.registry.register("jvm.memory", MemoryUsageGaugeSet())
            configuration.registry.register("jvm.garbage", GarbageCollectorMetricSet())
            configuration.registry.register("jvm.threads", ThreadStatesGaugeSet())
            configuration.registry.register("jvm.files", FileDescriptorRatioGauge())
            configuration.registry.register("jvm.attributes", JvmAttributeGaugeSet())

            val phase = PipelinePhase("Metrics")
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Monitoring, phase)
            pipeline.intercept(phase) {
                feature.before(call)
                try {
                    proceed()
                } catch (e: Exception) {
                    feature.exception(call, e)
                    throw e
                } finally {
                    feature.after(call)
                }
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallStarted) { call ->
                val name = call.route.toString()
                val meter = feature.registry.meter(MetricRegistry.name(name, "meter"))
                val timer = feature.registry.timer(MetricRegistry.name(name, "timer"))
                meter.mark()
                val context = timer.time()
                call.attributes.put(routingMetricsKey, RoutingMetrics(name, context))
            }

            pipeline.environment.monitor.subscribe(Routing.RoutingCallFinished) { call ->
                val routingMetrics = call.attributes.take(routingMetricsKey)
                val status = call.response.status()?.value ?: 0
                val statusMeter = feature.registry.meter(MetricRegistry.name(routingMetrics.name, status.toString()))
                statusMeter.mark()
                routingMetrics.context.stop()
            }

            return feature
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