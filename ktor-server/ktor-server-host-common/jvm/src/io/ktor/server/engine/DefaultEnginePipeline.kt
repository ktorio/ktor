package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.util.cio.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.util.pipeline.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.io.*
import java.nio.channels.*
import java.util.concurrent.CancellationException

/**
 * Default engine pipeline for all engines. Use it only if you are writing your own application engine implementation.
 */
@EngineAPI
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
        } finally {
            try {
                call.request.receiveChannel().discard()
            } catch (ignore: Throwable) {
            } finally {
            }
        }
    }

    return pipeline
}

private fun ApplicationEnvironment.logFailure(call: ApplicationCall, cause: Throwable) {
    try {
        val status = call.response.status() ?: "Unhandled"
        val logString = try {
            call.request.toLogString()
        } catch (cause: Throwable) {
            "(request error: $cause)"
        }

        when (cause) {
            is CancellationException -> log.info("$status: $logString, cancelled")
            is ClosedChannelException -> log.info("$status: $logString, channel closed")
            is ChannelIOException -> log.info("$status: $logString, channel failed")
            else -> log.error("$status: $logString", cause)
        }
    } catch (oom: OutOfMemoryError) {
        try {
            log.error(cause)
        } catch (oomAttempt2: OutOfMemoryError) {
            System.err.print("OutOfMemoryError: ")
            System.err.println(cause.message)
        }
    }
}
