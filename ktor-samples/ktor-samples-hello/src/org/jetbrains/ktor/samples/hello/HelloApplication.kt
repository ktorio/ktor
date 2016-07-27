package org.jetbrains.ktor.samples.hello

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*

class HelloApplication(environment: ApplicationEnvironment) : Application(environment) {
    init {
        install(DefaultHeaders)
        install(CallLogging)
        routing {
            get("/") {
                call.respondText(ContentType.Text.Plain, "Hello, World!")
            }
        }
    }
}
