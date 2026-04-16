/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.internal.*
import io.ktor.server.logging.*
import io.ktor.server.plugins.*
import io.ktor.server.request.httpVersion
import io.ktor.server.response.*
import io.ktor.server.routing.routingCallKey
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.io.IOException

/**
 * Default engine pipeline for all engines. Use it only if you are writing your own application engine implementation.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.defaultEnginePipeline)
 */
public fun defaultEnginePipeline(config: ApplicationConfig, developmentMode: Boolean): EnginePipeline {
    val pipeline = EnginePipeline(developmentMode)

    configureShutdownUrl(config, pipeline)

    pipeline.intercept(EnginePipeline.Call) {
        try {
            call.application.execute(call)
        } catch (error: ChannelIOException) {
            call.application.mdcProvider.withMDCBlock(call) {
                call.application.environment.logFailure(call, error)
            }
        } catch (error: Throwable) {
            val routeCall = call.attributes.getOrNull(routingCallKey)
            if (routeCall != null) {
                handleFailure(routeCall, error)
            } else {
                handleFailure(call, error)
            }
        } finally {
            try {
                val version = HttpProtocolVersion.parse(call.request.httpVersion)
                if (version.major == 1) {
                    // In HTTP/1.1, we should read the entire request body to reuse the persistent connection
                    // HTTP/2 and higher don't require draining the input to reuse it
                    call.request.receiveChannel().discard()
                }
            } catch (_: Throwable) {
            }
        }
    }

    return pipeline
}

/**
 * Logs the [error] and responds with an appropriate error status code.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.handleFailure)
 */
public suspend fun handleFailure(call: ApplicationCall, error: Throwable) {
    logError(call, error)
    val statusCode = defaultExceptionStatusCode(error) ?: HttpStatusCode.InternalServerError
    tryRespondError(call, statusCode, error.message)
}

/**
 * Logs the [error] with MDC setup.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.logError)
 */
public suspend fun logError(call: ApplicationCall, error: Throwable) {
    call.application.mdcProvider.withMDCBlock(call) {
        call.application.environment.logFailure(call, error)
    }
}

/**
 * Map [cause] to the corresponding status code or `null` if no default exception mapping for this [cause] type
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.engine.defaultExceptionStatusCode)
 */
public fun defaultExceptionStatusCode(cause: Throwable): HttpStatusCode? = when (cause) {
    is BadRequestException -> HttpStatusCode.BadRequest
    is NotFoundException -> HttpStatusCode.NotFound
    is UnsupportedMediaTypeException -> HttpStatusCode.UnsupportedMediaType
    is PayloadTooLargeException -> HttpStatusCode.PayloadTooLarge
    is TimeoutException, is TimeoutCancellationException -> HttpStatusCode.GatewayTimeout
    else -> null
}

private suspend fun tryRespondError(call: ApplicationCall, statusCode: HttpStatusCode, message: String?) {
    if (call.response.isCommitted || call.response.isSent) return
    try {
        when (message) {
            null -> call.respond(statusCode)
            else -> call.respond(statusCode, message)
        }
    } catch (_: BaseApplicationResponse.ResponseAlreadySentException) {
    }
}

private fun ApplicationEnvironment.logFailure(call: ApplicationCall, cause: Throwable) {
    try {
        val status = call.response.status() ?: "Unhandled"
        val logString = try {
            call.request.toLogString()
        } catch (cause: Throwable) {
            "(request error: $cause)"
        }

        val infoString = "$status: $logString. Exception ${cause::class}: ${cause.message}"
        when (cause) {
            is CancellationException,
            is ClosedChannelException,
            is ChannelIOException,
            is IOException,
            is BadRequestException,
            is NotFoundException,
            is PayloadTooLargeException,
            is UnsupportedMediaTypeException -> log.debug(infoString, cause)

            else -> log.error("$status: $logString", cause)
        }
    } catch (_: OutOfMemoryError) {
        try {
            log.error(cause)
        } catch (_: OutOfMemoryError) {
            printError("OutOfMemoryError: ")
            printError(cause.message)
            printError("\n")
        }
    }
}
