package io.ktor.client.tests.utils.tests

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Application.serializationTestServer() {
    routing {
        route("/json") {
            get("/users") {
                call.respondText("[{'id': 42, 'login': 'TestLogin'}]", contentType = ContentType.Application.Json)
            }
            get("/photos") {
                call.respondText("[{'id': 4242, 'path': 'cat.jpg'}]", contentType = ContentType.Application.Json)
            }
        }
    }
}
