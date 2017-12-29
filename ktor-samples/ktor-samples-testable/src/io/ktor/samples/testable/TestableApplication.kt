package io.ktor.samples.testable

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*

fun Application.testableApplication() {
    intercept(ApplicationCallPipeline.Call) {
        if (call.request.uri == "/")
            call.respondText("Test String")
    }
}
