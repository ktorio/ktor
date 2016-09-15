package org.jetbrains.ktor.features

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*

fun Pipeline<ApplicationCall>.statusPage(phase: PipelinePhase = ApplicationCallPipeline.Infrastructure, handler: PipelineContext<ApplicationCall>.(HttpStatusCode) -> Unit) {
    intercept(phase) { call ->
        var handled = false
        call.response.pipeline.intercept(RespondPipeline.After) {
            val obj = subject.message
            val status = when (obj) {
                is FinalContent -> obj.status
                is HttpStatusCode -> obj
                else -> null
            }

            if (status != null && !handled) {
                handled = true
                handler(status)
            }
        }
    }
}

fun Pipeline<ApplicationCall>.errorPage(phase: PipelinePhase = ApplicationCallPipeline.Infrastructure, handler: PipelineContext<ApplicationCall>.(Throwable) -> Unit) {
    intercept(phase) { call ->
        onFail {
            if (call.response.status() == null) {
                handler(exception!!)
            }
        }
    }
}
