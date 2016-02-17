package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

private fun Application.logCallResult(call: ApplicationCall) {

    val status = call.response.status()
    when (status) {
        HttpStatusCode.Found -> config.log.trace("$status: ${call.request.requestLine} -> ${call.response.headers[HttpHeaders.Location]}")
        else -> config.log.trace("$status: ${call.request.requestLine}")
    }
}

fun Application.logApplicationCalls() {
    intercept { call ->
        exit { logCallResult(call) }
    }
}