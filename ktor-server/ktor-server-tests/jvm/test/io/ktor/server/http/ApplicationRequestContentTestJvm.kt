/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.http

import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApplicationRequestContentTest {

    @Test
    fun testInputStreamContent() = testApplication {
        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals("bodyContent", call.receiveStream().reader(Charsets.UTF_8).readText())
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveStreams() = testApplication {
        install(DoubleReceive)

        application {
            intercept(ApplicationCallPipeline.Call) {
                assertEquals(11, call.receiveStream().readBytes().size)
                assertEquals(11, call.receiveStream().readBytes().size)
            }
        }

        client.get("") {
            setBody("bodyContent")
        }
    }

    @Test
    fun testDoubleReceiveRaceCondition() = runTest {
        (1..100).map {
            launch {
                testApplication {
                    application {
                        install(DoubleReceive) {}

                        install(StatusPages) {
                            status(HttpStatusCode.BadRequest) { call, status ->
                                call.respondText(text = "400: Bad Request", status = status)
                            }
                        }
                        routing {
                            post("/") {
                                val request = call.receiveText()
                                call.respond(HttpStatusCode.BadRequest, request)
                            }
                        }
                    }

                    client.post("/") {
                        setBody("Hello World")
                    }.also {
                        assertEquals(HttpStatusCode.BadRequest, it.status)
                    }
                }
            }
        }.joinAll()
    }
}
