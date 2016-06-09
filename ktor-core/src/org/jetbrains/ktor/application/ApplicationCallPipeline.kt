package org.jetbrains.ktor.application

import org.jetbrains.ktor.pipeline.*

val PipelineContext<ApplicationCall>.call: ApplicationCall get() = subject

open class ApplicationCallPipeline : Pipeline<ApplicationCall>(Infrastructure, Call, Fallback) {
    companion object ApplicationPhase {
        val Infrastructure = PipelinePhase("Infrastructure")
        val Call = PipelinePhase("Call")
        val Fallback = PipelinePhase("Fallback")
    }
}

class ResponsePipelineState(var obj: Any)

open class RespondPipeline : Pipeline<ResponsePipelineState>(Before, Transform, Render, After) {
    companion object RespondPhase {
        val Before = PipelinePhase("Before")

        val Transform = PipelinePhase("Transform")

        val Render = PipelinePhase("Render")

        val After = PipelinePhase("After")
    }
}