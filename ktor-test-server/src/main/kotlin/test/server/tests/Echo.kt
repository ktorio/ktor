/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.echoTest() {
    routing {
        route("/echo") {
            route("/method") {
                handle {
                    val method = call.request.local.method.value
                    // Some methods don't allow body, so return result in a header
                    call.response.header("Http-Method", method)
                    call.response.status(HttpStatusCode.OK)
                }
            }
        }
    }
}
