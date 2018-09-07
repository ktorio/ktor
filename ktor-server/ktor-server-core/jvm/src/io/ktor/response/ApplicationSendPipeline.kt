package io.ktor.response

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

/**
 * Server response send pipeline
 */
open class ApplicationSendPipeline : Pipeline<Any, ApplicationCall>(Before, Transform, Render, ContentEncoding, TransferEncoding, After, Engine) {
    /**
     * Send pipeline phases
     */
    @Suppress("PublicApiImplicitType")
    companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        val Before = PipelinePhase("Before")

        /**
         * Transformation phase that can proceed with any supported data like String
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Phase to render any current pipeline subject into [io.ktor.http.content.OutgoingContent]
         *
         * Beyond this phase only [io.ktor.http.content.OutgoingContent] should be produced by any interceptor
         */
        val Render = PipelinePhase("Render")

        /**
         * Phase for processing Content-Encoding, like compression and partial content
         */
        val ContentEncoding = PipelinePhase("ContentEncoding")

        /**
         * Phase for handling Transfer-Encoding, like if chunked encoding is being done manually and not by engine
         */
        val TransferEncoding = PipelinePhase("TransferEncoding")

        /**
         * The latest application phase that happens right before engine will send the response
         */
        val After = PipelinePhase("After")

        /**
         * Phase for Engine to send the response out to client.
         *
         * TODO: this phase will be removed from here later
         */
        @KtorExperimentalAPI
        val Engine = PipelinePhase("Engine")
    }
}
