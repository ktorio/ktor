/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.server.cio

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.cio.backend.*
import io.ktor.server.engine.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.random.*
import kotlin.test.*

/**
 * This is just an example demonstrating how to create CIO low-level http server
 *
 * uncomment `@Test` to run
 */
class RAWExample {
    //    @Test
    fun runEmbeddedServer() {
        embeddedServer(
            CIO,
            applicationEngineEnvironment {
                developmentMode = true
                connector {
                    port = Random.nextInt(8500, 9000)
                }
                module {
                    install(CallLogging)
                    routing {
                        get("route1") {
                            println("REQ1")
                            call.respond("Route 1 hello")
                        }
                        get("route2") {
                            println("REQ2")
                            call.respond("Route 2 hello")
                        }
                    }
                }
            }
        ).start(wait = true)
    }

    //    @Test
    fun runHttpServer() {
        val HelloWorld = "Hello, World!".toByteArray()
        val HelloWorldLength = HelloWorld.size.toString()

        val notFound404_11 = RequestResponseBuilder().apply {
            responseLine("HTTP/1.1", 404, "Not Found")
            headerLine("Content-Length", "0")
            emptyLine()
        }.build()

        val settings = HttpServerSettings()
        val server = GlobalScope.httpServer(
            settings,
            handler = { request ->
                try {
                    if (request.uri.length == 1 && request.uri[0] == '/' && request.method == HttpMethod.Get) {
                        val response = RequestResponseBuilder()
                        response.responseLine(request.version, 200, "OK")
                        response.headerLine("Date", GMTDate().toHttpDate())
                        response.headerLine("Content-Length", HelloWorldLength)
                        response.headerLine("Content-Type", "text/plain; charset=utf-8")
                        response.emptyLine()

                        response.bytes(HelloWorld)
                        output.writePacket(response.build())
                    } else {
                        output.writePacket(notFound404_11.copy())
                    }

                    output.close()
                } finally {
                    request.release()
                }
            }
        )

        runBlocking {
            server.rootServerJob.join()
        }
    }
}
