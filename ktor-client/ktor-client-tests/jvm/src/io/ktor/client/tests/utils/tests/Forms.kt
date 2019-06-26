/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlin.test.*

internal fun Application.fullFormTest() {
    routing {
        route("/forms") {
            get("/hello") {
                call.respondText("Hello, client")
            }
            post("/hello") {
                assertEquals("Hello, server", call.receive())
                call.respondText("Hello, client")
            }
            get("/custom") {
                call.respond(HttpStatusCode(200, "Custom"), "OK")
            }
        }
    }
}
