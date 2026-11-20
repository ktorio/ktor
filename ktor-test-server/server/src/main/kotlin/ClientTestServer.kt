/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import test.server.tests.*

internal fun Application.tests(verbose: Boolean) {
    install(WebSockets) {
        maxFrameSize = 4 * 1024L

        extensions {
            install(WebSocketDeflateExtension)
        }
    }

    if (verbose) {
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                cause.printStackTrace()
                call.respondText("An exception occurred in the test server", status = HttpStatusCode.InternalServerError)
            }
        }
    }

    authTestServer()
    echoTest()
    encodingTestServer()
    serializationTestServer()
    cacheTestServer()
    loggingTestServer()
    contentTestServer()
    fullFormTest()
    redirectTest()
    pluginsTest()
    webSockets()
    multiPartFormDataTest()
    headersTestServer()
    timeoutTest()
    cookiesTest()
    buildersTest()
    downloadTest()
    uploadTest()
    jsonTest()
    multithreadedTest()
    eventsTest()
    bomTest()
    serverSentEvents()

    routing {
        get("/") {
            call.respondText("Hello, world!")
        }
        post("/echo") {
            val response = call.receiveText()
            call.respond(response)
        }
        get("/echo_query") {
            val parameters = call.request.queryParameters.entries().joinToString { "${it.key}=${it.value}" }
            call.respondText(parameters)
        }
        post("/echo-with-content-type") {
            val response = call.receiveText()
            val contentType = call.request.header(HttpHeaders.ContentType)?.let { ContentType.parse(it) }
            call.respondBytes(response.toByteArray(), contentType)
        }
        get("/bytes") {
            val size = call.request.queryParameters["size"]!!.toInt()
            call.respondBytes(makeArray(size))
        }
        post("/content-type") {
            val contentType = call.request.header(HttpHeaders.ContentType)
            call.respondText(contentType ?: "")
        }
        delete("/delete") {
            call.respondText("OK ${call.receiveText()}")
        }
    }
}

internal suspend fun ApplicationCall.fail(text: String): Nothing {
    respondText(text, status = HttpStatusCode(400, text))
    error(text)
}
