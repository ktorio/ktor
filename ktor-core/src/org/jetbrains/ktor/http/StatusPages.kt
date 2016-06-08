package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.pipeline.*

fun Pipeline<ApplicationCall>.statusPage(phase: PipelinePhase = ApplicationCallPipeline.ApplicationPhase.Infrastructure, handler: PipelineContext<ApplicationCall>.(HttpStatusCode) -> Unit) {
    intercept(phase) { call ->
        var handled = false
        call.interceptRespond(RespondPipeline.Before) { obj ->
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

fun Pipeline<ApplicationCall>.errorPage(phase: PipelinePhase = ApplicationCallPipeline.ApplicationPhase.Infrastructure, handler: PipelineContext<ApplicationCall>.(Throwable) -> Unit) {
    intercept(phase) { call ->
        onFail { cause ->
            if (call.response.status() == null) {
                handler(cause)
            }
        }
    }
}
