/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

@OptIn(ExperimentalStdlibApi::class)
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
                    """.trimIndent(),
                    contentType = ContentType.Application.Json
                )
            }
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
