package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

class CallLogging(val log: ApplicationLog, val logSuccess: Boolean) {

    class Configuration {
        var logSuccess = true
    }

    companion object Feature : ApplicationFeature<Application, CallLogging.Configuration, CallLogging> {
        override val key: AttributeKey<CallLogging> = AttributeKey("Call Logging")
        override fun install(pipeline: Application, configure: Configuration.() -> Unit): CallLogging {
            val loggingPhase = PipelinePhase("Logging")
            val configuration = Configuration().apply(configure)
            val feature = CallLogging(pipeline.environment.log, configuration.logSuccess)
            pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
            pipeline.intercept(loggingPhase) { call ->
                try {
                    proceed()
                    if (feature.logSuccess)
                        feature.logSuccess(call)
                } catch(t: Throwable) {
                    feature.logFailure(call, t)
                }
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

    private fun logFailure(call: ApplicationCall, e: Throwable) {
        try {
            val status = call.response.status() ?: "Unhandled"
            log.error("$status: ${call.request.logInfo()}", e)
        } catch (oom: OutOfMemoryError) {
            try {
                log.error(e)
            } catch (oomAttempt2: OutOfMemoryError) {
                System.err.print("OutOfMemoryError: ")
                System.err.println(e.message)
            }
        }
    }

    private fun ApplicationRequest.logInfo() = "${httpMethod.value} - ${path()}"
}