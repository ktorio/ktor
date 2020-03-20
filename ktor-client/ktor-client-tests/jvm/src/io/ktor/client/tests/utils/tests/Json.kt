/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*


fun Application.jsonTest() {
    routing {
        route("json") {
            get("user-generic") {
                call.respondText(
                    """
                    {
                        "message": "ok",
                        "data": { "name": "hello" }
                    }
                """.trimIndent(), contentType = ContentType.Application.Json
                )
            }
        }
    }
}
