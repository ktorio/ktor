/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.pluginsTest() {
    routing {
        route("plugins") {
            get("body") {
                val size = call.parameters["size"]!!.toInt()
                val text = "x".repeat(size)
                call.respondText(text)
            }
            get("echo") {
                call.respondText("Hello, world")
            }
        }
    }
}
