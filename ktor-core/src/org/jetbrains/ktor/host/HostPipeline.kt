package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*

// TODO to be moved to the host common module

fun defaultHostPipeline() = HostPipeline().apply {
    intercept(HostPipeline.Before, {
        onFinish {
            subject.close()
        }
    })
    intercept(HostPipeline.Call) {
        fork(subject, subject.application)
    }
}

class HostPipeline : Pipeline<ApplicationCall>(Before, Call) {
    companion object {
        val Before = PipelinePhase("before")
        val Call = PipelinePhase("call")
    }
}

