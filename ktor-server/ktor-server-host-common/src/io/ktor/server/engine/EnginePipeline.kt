package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.util.pipeline.*

class EnginePipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
    }
}

