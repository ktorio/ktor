/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests.utils

import io.ktor.application.*
import io.ktor.client.tests.utils.tests.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.websocket.*

internal fun Application.tests() {
    install(WebSockets) {
        maxFrameSize = 4 * 1024
    }

    authTestServer()
    encodingTestServer()
    serializationTestServer()
    cacheTestServer()
    loggingTestServer()
    contentTestServer()
    fullFormTest()
    redirectTest()
    featuresTest()
    webSockets()
    multiPartFormDataTest()
    headersTestServer()
    timeoutTest()
    cookiesTest()
    buildersTest()
    downloadTest()
    uploadTest()
    jsonTest()

    routing {
        post("/echo") {
            val response = call.receiveText()
            call.respond(response)
        }
        post("/echo-with-content-type") {
            val response = call.receiveText()
            val contentType =
                call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
            call.respondBytes(response.toByteArray(), contentType)
        }
        get("/bytes") {
            val size = call.request.queryParameters["size"]!!.toInt()
            call.respondBytes(makeArray(size))
        }
    }
}

internal suspend fun ApplicationCall.fail(text: String): Nothing {
    respondText(text, status = HttpStatusCode(400, text))
    error(text)
}
