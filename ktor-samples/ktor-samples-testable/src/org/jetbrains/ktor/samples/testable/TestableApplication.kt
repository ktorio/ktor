package org.jetbrains.ktor.samples.testable

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

class TestableApplication(config: ApplicationConfig) : Application(config) {
    init {
        routing {
            get("/") {
                response.sendText("Test String")
            }
        }
    }
}
