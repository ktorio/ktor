package io.ktor.metrics

import io.ktor.application.*
import io.ktor.auth.AuthenticationRouteSelector
import io.ktor.pipeline.*
import io.ktor.request.httpMethod
import io.ktor.routing.*
import io.ktor.util.*
import io.micrometer.core.instrument.*
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.binder.jvm.*
import io.micrometer.core.instrument.binder.system.*
import java.util.concurrent.atomic.AtomicInteger

class Metrics(val registry: MeterRegistry) {
	val baseName: String = "ktor.http.server"
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
	}

	companion object Feature : ApplicationFeature<Application, Configuration, Metrics> {
		override val key = AttributeKey<Metrics>("metrics")

		val Route.cleanPath: String get() = when {
			parent == null -> "/"
			else ->
				if (selector is AuthenticationRouteSelector || selector is HttpMethodRouteSelector) {
					"${(parent as Route).cleanPath}"
				} else {
					"${(parent as Route).cleanPath}/$selector".trim('/')
				}
		}

		override fun install(pipeline: Application, configure: Configuration.() -> Unit): Metrics {
			val configuration = Configuration().apply(configure)
			val feature = Metrics(configuration.registry)

			configuration.meterBinders.forEach { it.bindTo(configuration.registry) }

			val phase = PipelinePhase("Metrics")
			pipeline.insertPhaseBefore(ApplicationCallPipeline.Infrastructure, phase)
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
				val path = "/" + call.route.cleanPath

				with(feature) {
					recordRequest(call.getLocalAddr(), call.request.httpMethod.value, path)
				}
			}

			return feature
		}
	}


	private data class CallMeasure(val timer: Timer.Sample)

	private val measureKey = AttributeKey<CallMeasure>("metrics")

	private fun ApplicationCall.getLocalAddr() = request.local.let { "${it.host}:${it.port}" }

	private fun ApplicationCall.getStatusCode() =
			response.status()?.value?.toString() ?: "Unknown"

	private fun recordError(localAddr: String, errorClass: String) {
		registry.counter("$baseName.errors",
				"local", localAddr,
				"class", errorClass)
				.increment()
	}

	private fun Timer.Sample.recordDuration(localAddr: String) {
		stop(registry.timer(
				"$baseName.requests",
				"local", localAddr
		))
	}

	private fun recordRequest(localAddr: String, method: String, path: String) {
		registry.counter("$baseName.requests",
				"local", localAddr,
				"method", method,
				"path", path
		).increment()
	}

	private fun recordResponse(localAddr: String, statusCode: String) {
		registry.counter("$baseName.responses",
				"local", localAddr,
				"code", statusCode
		).increment()
	}

	private fun before(call: ApplicationCall) {
		active.incrementAndGet()

		call.attributes.put(measureKey, CallMeasure(Timer.start(registry)))
	}

	private fun after(call: ApplicationCall) {
		active.decrementAndGet()

		recordResponse(call.getLocalAddr(), call.getStatusCode())

		call.attributes.getOrNull(measureKey)?.apply {
			timer.recordDuration(call.getLocalAddr())
		}
	}

	@Suppress("UNUSED_PARAMETER")
	private fun exception(call: ApplicationCall, e: Throwable) {
		recordError(call.getLocalAddr(), e::class.java.simpleName)
	}
}
