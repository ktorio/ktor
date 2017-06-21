package org.jetbrains.ktor.samples.testable

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*

fun Application.testableApplication() {
    intercept(ApplicationCallPipeline.Call) {
        if (call.request.uri == "/")
            call.respondText("Test String")
    }
}
