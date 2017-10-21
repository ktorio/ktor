package io.ktor.application

import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*

open class ApplicationCallPipeline : Pipeline<Unit, ApplicationCall>(Infrastructure, Call, Fallback) {
    /**
     * Pipeline for receiving content
     */
    val receivePipeline = ApplicationReceivePipeline()

    /**
     * Pipeline for sending content
     */
    val sendPipeline = ApplicationSendPipeline()

    /**
     * Standard phases for application call pipelines
     */
    companion object ApplicationPhase {
        val Infrastructure = PipelinePhase("Infrastructure")
        val Call = PipelinePhase("Call")
        val Fallback = PipelinePhase("Fallback")
    }
}

/**
 * Current call for the context
 */
val PipelineContext<*, ApplicationCall>.call: ApplicationCall get() = context

/**
 * Current application for the context
 */
val PipelineContext<*, ApplicationCall>.application get() = call.application
