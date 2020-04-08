/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.client.tests.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import kotlin.test.*

class CIORequestTest : TestWithKtor() {
    private val testSize = 2 * 1024

    override val server: ApplicationEngine = embeddedServer(Netty, serverPort) {
        routing {
            get("/") {
                val longHeader = call.request.headers["LongHeader"]!!
                call.respond(object : OutgoingContent.NoContent() {
                    override val headers: Headers = headersOf("LongHeader", longHeader)
                })
            }
            get("/echo") {
                call.respond("OK")
            }
        }
    }

    @Test
    fun longHeadersTest() = clientTest(CIO) {
        test { client ->
            val headerValue = "x".repeat(testSize)

            client.get<HttpResponse>(port = serverPort) {
                header("LongHeader", headerValue)
            }.use { response ->
                assertEquals(headerValue, response.headers["LongHeader"])
            }
        }
    }

    @Test
    fun testHanging() = clientTest(CIO) {
        config {
            engine {
                endpoint {
                    connectTimeout = 1
                }
            }
        }

        test { client ->
            withTimeout(5000) {
                for (i in 0..1000) {
                    try {
                        client.get<String>("http://something.wrong")
                    } catch (cause: CancellationException) {
                        throw cause
                    } catch (cause: Throwable) {
                        // ignore
                    }
                }
            }
        }
    }
}
