package org.jetbrains.ktor.samples.testable

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*

class TestableApplication(config: ApplicationConfig) : Application(config) {
    init {
        intercept { next ->
            if (request.uri == "/")
                response.sendText("Test String")
            else
                next()
        }
    }
}
