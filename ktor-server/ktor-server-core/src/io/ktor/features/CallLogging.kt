package io.ktor.features

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.util.*
import org.slf4j.*

/**
 * Logs application lifecycle and call events.
 */
class CallLogging(private val log: Logger, private val monitor: ApplicationEvents) {

    class Configuration

    private val starting: (Application) -> Unit = { it.log.trace("Application starting: $it") }
    private val started: (Application) -> Unit = { it.log.trace("Application started: $it") }
    private val stopping: (Application) -> Unit = { it.log.trace("Application stopping: $it") }
    private var stopped: (Application) -> Unit = {}

    init {
        stopped = {
            it.log.trace("Application stopped: $it")
            monitor.unsubscribe(ApplicationStarting, starting)
            monitor.unsubscribe(ApplicationStarted, started)
            monitor.unsubscribe(ApplicationStopping, stopping)
            monitor.unsubscribe(ApplicationStopped, stopped)
        }

        monitor.subscribe(ApplicationStarting, starting)
        monitor.subscribe(ApplicationStarted, started)
        monitor.subscribe(ApplicationStopping, stopping)
        monitor.subscribe(ApplicationStopped, stopped)
    }

    /**
     * Installable feature for [CallLogging].
     */
    companion object Feature : ApplicationFeature<Application, Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            Configuration().apply(configure)
            val feature = CallLogging(pipeline.log, pipeline.environment.monitor)
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
            pipeline.intercept(loggingPhase) {
                proceed()
                feature.logSuccess(call)
            }
            return feature
        }

    }


    private fun logSuccess(call: ApplicationCall) {
        val status = call.response.status() ?: "Unhandled"
        when (status) {
            HttpStatusCode.Found -> log.trace("$status: ${call.request.logInfo()} -> ${call.response.headers[HttpHeaders.Location]}")
            else -> log.trace("$status: ${call.request.logInfo()}")
        }
    }
}

fun ApplicationRequest.logInfo() = "${httpMethod.value} - ${path()}"
