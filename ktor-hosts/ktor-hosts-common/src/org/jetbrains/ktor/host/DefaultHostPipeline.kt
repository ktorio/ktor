package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*

fun defaultHostPipeline(environment: ApplicationEnvironment) = HostPipeline().apply {
    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        install(ShutDownUrl.HostFeature) {
            shutDownUrl = url
        }
    }

    intercept(HostPipeline.Call) {
        try {
            call.application.execute(call)
            if (call.response.status() == null) {
                call.respond(HttpStatusContent(HttpStatusCode.NotFound, "Cannot find resource with the requested URI: ${call.request.uri}"))
            }
        } catch (error: Throwable) {
            environment.log.error("Failed to process request", error)
            call.respond(HttpStatusContent(HttpStatusCode.InternalServerError, "${error.javaClass.simpleName}: ${error.message}\n"))
        }
    }
}


