/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.loggingTestServer() {
    routing {
        route("logging") {
            get {
                call.respondText("home page")
            }
            post {
                if ("Response data" != call.receiveText()) {
                    call.respond(HttpStatusCode.BadRequest)
                } else {
                    call.respondText("/", status = HttpStatusCode.Created)
                }
            }
            get("301") {
                call.respondRedirect("/logging")
            }
        }
    }
}
