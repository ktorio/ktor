package io.ktor.application

import io.ktor.util.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import kotlinx.coroutines.*

/**
 * Pipeline configuration for executing [ApplicationCall] instances
 */
@Suppress("PublicApiImplicitType")
open class ApplicationCallPipeline : Pipeline<Unit, ApplicationCall>(Setup, Monitoring, Features, Call, Fallback) {
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
         * Phase for preparing call and it's attributes for processing
         */
        val Setup = PipelinePhase("Setup")

        /**
         * Phase for tracing calls, useful for logging, metrics, error handling and so on
         */
        val Monitoring = PipelinePhase("Monitoring")

        /**
         * Phase for features. Most features should intercept this phase.
         */
        val Features = PipelinePhase("Features")

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
val PipelineContext<*, ApplicationCall>.application: Application get() = call.application
