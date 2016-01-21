package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*

private fun Application.logCallResult(call: ApplicationCall, result: ApplicationCallResult) {
    val request = call.request
    val response = call.response

    when (result) {
        ApplicationCallResult.Handled -> {
            val status = response.status()
            when (status) {
                HttpStatusCode.Found -> config.log.trace("$status: ${request.requestLine} -> ${response.headers[HttpHeaders.Location]}")
                else -> config.log.trace("$status: ${request.requestLine}")
            }
        }
        ApplicationCallResult.Unhandled -> config.log.trace("<Unhandled>: ${request.requestLine}")
        ApplicationCallResult.Asynchronous -> config.log.trace("<Async>: ${request.requestLine}")
    }
}

fun Application.logApplicationCalls() {
    intercept { next -> next().apply { logCallResult(this@intercept, this) } }
}