package io.ktor.samples.feature

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.response.*
import io.ktor.routing.*

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
