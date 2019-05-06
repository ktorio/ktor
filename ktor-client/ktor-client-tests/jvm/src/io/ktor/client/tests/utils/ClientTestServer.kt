/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.tests.utils.tests.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.websocket.*
import kotlin.test.*

fun Application.tests() {
    install(WebSockets)

    authTestServer()
    encodingTestServer()
    serializationTestServer()
    cacheTestServer()
    loggingTestServer()
    contentTestServer()

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
