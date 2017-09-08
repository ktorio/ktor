package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*
import org.slf4j.*
import org.slf4j.event.*

class CallLogging(private val log: Logger, private val monitor: ApplicationMonitor, configuration: Configuration) {
    private val level = configuration.level
    
    class Configuration {
        var level: Level = Level.TRACE
    }

    private val starting: (Application) -> Unit = { log("Application starting: $it") }
    private val started: (Application) -> Unit = { log("Application started: $it") }
    private val stopping: (Application) -> Unit = { log("Application stopping: $it") }
    private var stopped: (Application) -> Unit = {}

    init {
        stopped = {
            log("Application stopped: $it")
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

    companion object Feature : ApplicationFeature<Application, Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            val configuration = Configuration().apply(configure)
            val feature = CallLogging(pipeline.log, pipeline.environment.monitor, configuration)
            pipeline.insertPhaseBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
            pipeline.intercept(loggingPhase) {
                proceed()
                feature.logSuccess(call)
            }
            return feature
        }

    }

    private fun log(message: String) {
        when {
            level == Level.INFO && log.isInfoEnabled -> log.info(message)
            level == Level.DEBUG && log.isDebugEnabled -> log.debug(message)
            level == Level.TRACE && log.isTraceEnabled -> log.trace(message)
            else -> throw IllegalArgumentException("Call logging is not supported for levels higher than INFO.")
        }
    }

    private fun logSuccess(call: ApplicationCall) {
        val status = call.response.status() ?: "Unhandled"
        when (status) {
            HttpStatusCode.Found -> log("$status: ${call.request.logInfo()} -> ${call.response.headers[HttpHeaders.Location]}")
            else -> log("$status: ${call.request.logInfo()}")
        }
    }
}

fun ApplicationRequest.logInfo() = "${httpMethod.value} - ${path()}"
