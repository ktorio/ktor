package io.ktor.server.host

import io.ktor.application.*
import io.ktor.pipeline.*

class HostPipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
    }
}

