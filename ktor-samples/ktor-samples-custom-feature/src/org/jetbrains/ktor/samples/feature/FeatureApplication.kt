package org.jetbrains.ktor.samples.feature

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

fun Application.main() {
    install(DefaultHeaders)
    install(CallLogging)
    install(CustomHeader) { // Install a custom feature
        headerName = "Hello" // configuration
        headerValue = "World"
    }
    install(Routing) {
        get("/") {
            call.respondText("Hello, World!")
        }
    }
}
