/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server

import io.ktor.client.tests.utils.tests.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import test.server.tests.*

internal fun Application.tests() {
    install(WebSockets) {
        maxFrameSize = 4 * 1024L

        extensions {
            install(WebSocketDeflateExtension)
        }
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respondText("An exception occurred in the test server", status = HttpStatusCode.InternalServerError)
        }
    }

    authTestServer()
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

internal fun Application.tlsTests() {
    install(DefaultHeaders) {
        header("X-Comment", "TLS test server")
    }
    install(WebSockets)
    routing {
        get("/") {
            call.respondText("Hello, TLS!")
        }
        route("websockets") {
            webSocket("echo") {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val text = frame.readText()
                            send(Frame.Text(text))
                        }

                        is Frame.Binary -> send(Frame.Binary(fin = true, frame.data))
                        else -> error("Unsupported frame type: ${frame.frameType}.")
                    }
                }
            }
        }
    }
}

internal suspend fun ApplicationCall.fail(text: String): Nothing {
    respondText(text, status = HttpStatusCode(400, text))
    error(text)
}
