/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.uploadTest() {
    routing {
        route("upload") {
            post("content") {
                val message = call.request.headers[HttpHeaders.ContentType] ?: "EMPTY"
                call.respond(HttpStatusCode.OK, message)
            }
        }
    }
}
