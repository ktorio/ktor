package io.ktor.server.host

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.util.*
import java.nio.channels.*
import java.util.concurrent.*

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
        } catch (error: ChannelIOException) {
            call.application.environment.logFailure(call, error)
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
        when (e) {
            is CancellationException -> log.error("$status: ${call.request.logInfo()}, cancelled")
            is ClosedChannelException -> log.error("$status: ${call.request.logInfo()}, channel closed")
            is ChannelIOException -> log.error("$status: ${call.request.logInfo()}, channel failed")
            else -> log.error("$status: ${call.request.logInfo()}", e)
        }
    } catch (oom: OutOfMemoryError) {
        try {
            log.error(e)
        } catch (oomAttempt2: OutOfMemoryError) {
            System.err.print("OutOfMemoryError: ")
            System.err.println(e.message)
        }
    }
}



