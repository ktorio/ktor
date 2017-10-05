package io.ktor.host

import io.ktor.application.ApplicationCall
import io.ktor.pipeline.*

class HostPipeline : Pipeline<Unit, ApplicationCall>(Before, Call) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
    }
}

