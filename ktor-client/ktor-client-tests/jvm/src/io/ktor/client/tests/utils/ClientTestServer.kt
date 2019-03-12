package io.ktor.client.tests.utils

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.tests.utils.tests.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*

fun Application.tests() {
    install(WebSockets)

    authTestServer()
    encodingTestServer()
    serializationTestServer()
    cacheTestServer()

    routing {
        post("/echo") {
            val response = call.receiveText()
            call.respond(response)
        }
        get("/bytes") {
            val size = call.request.queryParameters["size"]!!.toInt()
            call.respondBytes(makeArray(size))
        }
    }
}
