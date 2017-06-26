package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

class CallLogging(val log: ApplicationLog, val monitor: ApplicationMonitor) {

    class Configuration

    val starting: (Application) -> Unit = { it.log.trace("Application starting: $it") }
    val started: (Application) -> Unit = { it.log.trace("Application started: $it") }
    val stopping: (Application) -> Unit = { it.log.trace("Application stopping: $it") }
    var stopped: (Application) -> Unit = {}

    init {
        stopped = {
            it.log.trace("Application stopped: $it")
            monitor.applicationStarting -= starting
            monitor.applicationStarted -= started
            monitor.applicationStopping -= stopping
            monitor.applicationStopped -= stopped
        }

        monitor.applicationStarting += starting
        monitor.applicationStarted += started
        monitor.applicationStopping += stopping
        monitor.applicationStopped += stopped
    }

    companion object Feature : ApplicationFeature<Application, CallLogging.Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            Configuration().apply(configure)
            val feature = CallLogging(pipeline.log, pipeline.environment.monitor)
            pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
            pipeline.intercept(loggingPhase) { call ->
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
