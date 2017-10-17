package io.ktor.client.request

import io.ktor.client.*
import io.ktor.pipeline.*


class HttpRequestPipeline : Pipeline<Any, HttpClient>(Before, State, Transform, Render, Send) {
    companion object Phases {
        val Before = PipelinePhase("Before")
        val State = PipelinePhase("State")
        val Transform = PipelinePhase("Transform")
        val Render = PipelinePhase("Render")
        val Send = PipelinePhase("Send")
    }
}
