package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

class HostPipeline : Pipeline<ApplicationCall>(Before, Call, Fallback) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
        val Fallback = PipelinePhase("fallback")
    }
}

