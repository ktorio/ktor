package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

private fun Application.logCallFinished(call: ApplicationCall) {
    val status = call.response.status()
    when (status) {
        HttpStatusCode.Found -> config.log.trace("$status: ${call.request.requestLine} -> ${call.response.headers[HttpHeaders.Location]}")
        else -> config.log.trace("$status: ${call.request.requestLine}")
    }
}

private fun Application.logCallFailed(call: ApplicationCall, e: Throwable) {
    val status = call.response.status()
    config.log.error("$status: ${call.request.requestLine}", e)
}

fun Application.logApplicationCalls() {
    intercept { call ->
        onSuccess { logCallFinished(call) }
        onFail { logCallFailed(call, it) }
    }
}