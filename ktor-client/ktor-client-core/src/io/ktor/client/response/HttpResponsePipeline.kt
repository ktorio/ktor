package io.ktor.client.response

import io.ktor.client.call.*
import io.ktor.pipeline.*
import kotlin.reflect.*


class HttpResponsePipeline : Pipeline<HttpResponseContainer, HttpClientCall>(Receive, Parse, Transform, State, After) {
    companion object Phases {
        /**
         * The earliest phase that happens before any other
         */
        val Receive = PipelinePhase("Receive")

        /**
         * Decode response body
         */
        val Parse = PipelinePhase("Parse")

        /**
         * Transform response body to expected format
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Use this phase to store request shared state
         */
        val State = PipelinePhase("State")

        /**
         * Latest response pipeline phase
         */
        val After = PipelinePhase("After")
    }
}

data class HttpResponseContainer(val expectedType: KClass<*>, val response: Any)
