package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*

fun ApplicationCallPipeline.setupDefaultHostPages(
        HostInfrastructurePhase: PipelinePhase = PipelinePhase("host-infrastructure"),
        HostFallbackPhase: PipelinePhase = PipelinePhase("host-fallback")
) {
    phases.insertAfter(ApplicationCallPipeline.Fallback, HostFallbackPhase)
    phases.insertBefore(ApplicationCallPipeline.Infrastructure, HostInfrastructurePhase)

    errorPage(HostInfrastructurePhase) { error ->
        call.respond(HttpStatusContent(HttpStatusCode.InternalServerError, "${error.javaClass.simpleName}: ${error.message}\n"))
    }

    intercept(HostFallbackPhase) { call ->
        call.respond(HttpStatusContent(HttpStatusCode.NotFound, "Not found: ${call.request.requestLine.uri}\n"))
    }
}
