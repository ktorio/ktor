/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.featuresTest() {
    routing {
        route("features") {
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
