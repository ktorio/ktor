package io.ktor.client.response

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.pipeline.*
import kotlin.reflect.*


class HttpResponsePipeline : Pipeline<HttpResponseContainer, HttpClient>(Receive, Parse, Transform, State, After) {
    companion object Phases {
        val Receive = PipelinePhase("Receive")
        val Parse = PipelinePhase("Parse")
        val Transform = PipelinePhase("Transform")
        val State = PipelinePhase("State")
        val After = PipelinePhase("After")
    }
}

data class HttpResponseContainer(val expectedType: KClass<*>, val request: HttpRequest, val response: HttpResponseBuilder)
