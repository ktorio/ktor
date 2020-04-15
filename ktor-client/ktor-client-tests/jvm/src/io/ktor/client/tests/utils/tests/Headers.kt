/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

internal fun Application.headersTestServer() {
    routing {
        route("/headers") {
            get("/") {
                call.response.header("X-Header-Single-Value", "foo")
                call.response.header("X-Header-Double-Value", "foo")
                call.response.header("X-Header-Double-Value", "bar")
                call.respond(HttpStatusCode.OK, "OK")
            }
        }

        route("/headers-merge") {
            accept(ContentType.Application.Json) {
                get("/") {
                    call.respondText("JSON", ContentType.Application.Json, HttpStatusCode.OK)
                }
            }
            accept(ContentType.Application.Xml) {
                get("/") {
                    call.respondText("XML", ContentType.Application.Xml, HttpStatusCode.OK)
                }
            }
        }
    }
}
