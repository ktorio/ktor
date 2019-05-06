/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.client.tests.utils.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import kotlin.test.*

internal fun Application.contentTestServer() {
    routing {
        route("/content") {
            post("/echo") {
                val content = call.request.receiveChannel().toByteArray()
                call.respond(content)
            }
            get("/news") {
                val form = call.request.queryParameters

                assertEquals("myuser", form["user"]!!)
                assertEquals("10", form["page"]!!)

                call.respond("100")
            }
            post("/sign") {
                val form = call.receiveParameters()

                assertEquals("myuser", form["user"]!!)
                assertEquals("abcdefg", form["token"]!!)

                call.respond("success")
            }
            post("/upload") {
                val parts = call.receiveMultipart().readAllParts()
                parts.forEach { part ->
                    assertEquals(part.contentDisposition?.disposition, "form-data")
                }

                call.respondText(parts.makeString())
            }
        }
    }
}
