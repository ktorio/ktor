/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.fullFormTest() {
    routing {
        route("/forms") {
            get("/hello") {
                call.respondText("Hello, client")
            }
            post("/hello") {
                val value = call.receive<String>()
                check("Hello, server" == value) { "Received: $value" }
                call.respondText("Hello, client")
            }
            get("/custom") {
                call.respond(HttpStatusCode(200, "Custom"), "OK")
            }
        }
    }
}
