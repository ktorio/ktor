package io.ktor.client.request

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.util.pipeline.*

/**
 * [HttpClient] Pipeline used for executing [HttpRequest].
 */
class HttpRequestPipeline : Pipeline<Any, HttpRequestBuilder>(Before, State, Transform, Render, Send) {
    /**
     * All interceptors accept payload as [subject] and try to convert it to [OutgoingContent]
     * Last phase should proceed with [HttpClientCall]
     */
    companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        val Before = PipelinePhase("Before")

        /**
         * Use this phase to modify request with shared state
         */
        val State = PipelinePhase("State")

        /**
         * Transform request body to supported render format
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Encode request body to [OutgoingContent]
         */
        val Render = PipelinePhase("Render")

        /**
         * Phase for [HttpSend] feature
         */
        val Send = PipelinePhase("Send")
    }
}

/**
 * [HttpClient] Pipeline used for sending [HttpRequest] to remote server.
 */
class HttpSendPipeline : Pipeline<Any, HttpRequestBuilder>(Before, State, Engine) {

    companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        val Before = PipelinePhase("Before")

        /**
         * Use this phase to modify request with shared state
         */
        val State = PipelinePhase("State")

        /**
         * Send request to remote server
         */
        val Engine = PipelinePhase("Engine")
    }
}
