package org.jetbrains.ktor.samples.testable

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*

class TestableApplication : ApplicationModule() {
    override fun Application.install() {
        intercept(ApplicationCallPipeline.Call) { call ->
            if (call.request.uri == "/")
                call.respondText("Test String")
        }
    }
}
