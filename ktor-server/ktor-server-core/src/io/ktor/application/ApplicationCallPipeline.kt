package io.ktor.application

import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.response.*

/**
 * Pipeline configuration for executing [ApplicationCall] instances
 */
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
        /**
         * Phase for setting up infrastructure for processing a call
         */
        val Infrastructure = PipelinePhase("Infrastructure")

        /**
         * Phase for processing a call and sending a response
         */
        val Call = PipelinePhase("Call")

        /**
         * Phase for handling unprocessed calls
         */
        val Fallback = PipelinePhase("Fallback")
    }
}

/**
 * Current call for the context
 */
inline val PipelineContext<*, ApplicationCall>.call: ApplicationCall get() = context

/**
 * Current application for the context
 */
val PipelineContext<*, ApplicationCall>.application get() = call.application
