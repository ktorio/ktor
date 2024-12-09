/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Application.serializationTestServer() {
    routing {
        route("/json") {
            get("/users") {
                call.respondText("[{\"id\": 42, \"login\": \"TestLogin\"}]", contentType = ContentType.Application.Json)
            }
            get("/users-long") {
                val users = buildList { repeat(300) { add("""{"id": $it, "login": "TestLogin-$it"}""") } }
                    .joinToString(",")
                call.respondText("[$users]", contentType = ContentType.Application.Json)
            }
            get("/photos") {
                call.respondText("[{\"id\": 4242, \"path\": \"cat.jpg\"}]", contentType = ContentType.Application.Json)
            }
        }
    }
}
