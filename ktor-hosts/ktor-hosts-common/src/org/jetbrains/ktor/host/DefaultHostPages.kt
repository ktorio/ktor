package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.request.*

fun Pipeline<ApplicationCall>.setupDefaultHostPages(hostPhase: PipelinePhase, hostFallbackPhase: PipelinePhase
) {
    intercept(hostPhase) {
        try {
            proceed()
            if (call.response.status() == null) {
                call.respond(HttpStatusContent(HttpStatusCode.NotFound, "Not found: ${call.request.path()}\n"))
            }
        } catch(error: Throwable) {
            call.respond(HttpStatusContent(HttpStatusCode.InternalServerError, "${error.javaClass.simpleName}: ${error.message}\n"))

        }
    }
}
