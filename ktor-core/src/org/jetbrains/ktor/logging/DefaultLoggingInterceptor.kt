package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.util.*

object CallLogging : ApplicationFeature<Application, Unit, Unit> {
    override val key: AttributeKey<Unit> = AttributeKey("Call Logging")

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        val loggingPhase = PipelinePhase("Logging")
        pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
        pipeline.intercept(loggingPhase) { call ->
            onSuccess { pipeline.logCallFinished(call) }
            onFail { pipeline.logCallFailed(call, it) }
        }
    }

    private fun Application.logCallFinished(call: ApplicationCall) {
        val status = call.response.status()
        when (status) {
            HttpStatusCode.Found -> environment.log.trace("$status: ${call.request.logInfo()} -> ${call.response.headers[HttpHeaders.Location]}")
            else -> environment.log.trace("$status: ${call.request.logInfo()}")
        }
    }

    private fun Application.logCallFailed(call: ApplicationCall, e: Throwable) {
        val status = call.response.status()
        environment.log.error("$status: ${call.request.logInfo()}", e)
    }

    private fun ApplicationRequest.logInfo() = "${httpMethod.value} - ${path()}"
}