package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

class HostPipeline : Pipeline<Unit>(Before, Call) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
    }
}

