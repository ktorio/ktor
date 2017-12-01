package io.ktor.client.request

import io.ktor.client.call.*
import io.ktor.pipeline.*


class HttpRequestPipeline : Pipeline<Any, HttpRequestContext>(Before, State, Transform, Render, Send) {
    /**
     * All interceptors should accept [HttpRequestBuilder] and proceed with [HttpRequestBuilder] or [HttpClientCall].
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
         * Send request to remote server
         */
        val Send = PipelinePhase("Send")
    }
}
