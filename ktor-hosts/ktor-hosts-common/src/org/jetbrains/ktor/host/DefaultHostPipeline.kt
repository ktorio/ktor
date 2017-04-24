package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.request.*

fun defaultHostPipeline(environment: ApplicationEnvironment): HostPipeline {
    val pipeline = HostPipeline()

    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        pipeline.install(ShutDownUrl.HostFeature) {
            shutDownUrl = url
        }
    }

    pipeline.intercept(HostPipeline.Call) {
        try {
            call.application.execute(call)
            if (call.response.status() == null) {
                call.respond(HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${call.request.uri}"))
            }
        } catch (error: Throwable) {
            call.application.environment.logFailure(call, error)
            call.respond(HttpStatusContent(HttpStatusCode.InternalServerError, "${error::class.simpleName}: ${error.message}\n"))
        }

    }

    return pipeline
}

private fun ApplicationEnvironment.logFailure(call: ApplicationCall, e: Throwable) {
    try {
        val status = call.response.status() ?: "Unhandled"
        log.error("$status: ${call.request.logInfo()}", e)
    } catch (oom: OutOfMemoryError) {
        try {
            log.error(e)
        } catch (oomAttempt2: OutOfMemoryError) {
            System.err.print("OutOfMemoryError: ")
            System.err.println(e.message)
        }
    }
}



