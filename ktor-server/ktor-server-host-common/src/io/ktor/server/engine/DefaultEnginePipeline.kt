package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import java.nio.channels.*
import java.util.concurrent.*

fun defaultEnginePipeline(environment: ApplicationEnvironment): EnginePipeline {
    val pipeline = EnginePipeline()

    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        pipeline.install(ShutDownUrl.EngineFeature) {
            shutDownUrl = url
        }
    }

    pipeline.intercept(EnginePipeline.Call) {
        try {
            call.application.execute(call)
            if (call.response.status() == null) {
                call.respond(HttpStatusCode.NotFound)
            }
        } catch (error: ChannelIOException) {
            call.application.environment.logFailure(call, error)
        } catch (error: Throwable) {
            call.application.environment.logFailure(call, error)
            try {
                call.respond(HttpStatusCode.InternalServerError)
            } catch (ignore: BaseApplicationResponse.ResponseAlreadySentException) {
            }
        }
    }

    return pipeline
}

private fun ApplicationEnvironment.logFailure(call: ApplicationCall, e: Throwable) {
    try {
        val status = call.response.status() ?: "Unhandled"
        when (e) {
            is CancellationException -> log.error("$status: ${call.request.toLogString()}, cancelled")
            is ClosedChannelException -> log.error("$status: ${call.request.toLogString()}, channel closed")
            is ChannelIOException -> log.error("$status: ${call.request.toLogString()}, channel failed")
            else -> log.error("$status: ${call.request.toLogString()}", e)
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



