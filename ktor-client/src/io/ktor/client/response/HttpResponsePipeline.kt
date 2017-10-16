package io.ktor.client.response

import io.ktor.client.pipeline.HttpClientScope
import io.ktor.client.request.HttpRequest
import io.ktor.pipeline.Pipeline
import io.ktor.pipeline.PipelinePhase
import kotlin.reflect.KClass


class HttpResponsePipeline : Pipeline<HttpResponseContainer, HttpClientScope>(Receive, Parse, Transform, State, After) {
    companion object Phases {
        val Receive = PipelinePhase("Receive")
        val Parse = PipelinePhase("Parse")
        val Transform = PipelinePhase("Transform")
        val State = PipelinePhase("State")
        val After = PipelinePhase("After")
    }
}

data class HttpResponseContainer(val expectedType: KClass<*>, val request: HttpRequest, val response: HttpResponseBuilder)
